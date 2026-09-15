package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.exception.ProdutoNotFoundException;
import com.quistock.ds_backend.model.dto.LoteErpDTO;
import com.quistock.ds_backend.model.dto.ProdutoDTO;
import com.quistock.ds_backend.util.ErpValueParser;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ProdutoService {
  private static final int MAX_VALUES_WITHOUT_DIVERGENCE = 1;

  private static final Logger LOGGER = LoggerFactory.getLogger(ProdutoService.class);
  private static final ParameterizedTypeReference<List<LoteErpDTO>> LOTES_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient erpRestClient;
  private final String produtosPath;
  private final Clock clock;

  public ProdutoService(
      RestClient restClient,
      @Value("${erp.api.produtos-path:/produto}") String path,
      Clock applicationClock) {
    this.erpRestClient = restClient;
    this.produtosPath = path;
    this.clock = applicationClock;
  }

  public List<ProdutoDTO> listarProdutos(String filial, String categoria, Boolean status) {
    return aplicarFiltros(obterProdutosConsolidados(), filial, categoria, status);
  }

  public ProdutoDTO buscarProdutoPorId(String id) {
    return obterProdutosConsolidados().stream()
        .filter(produto -> produto.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new ProdutoNotFoundException(id));
  }

  private List<ProdutoDTO> obterProdutosConsolidados() {
    try {
      List<LoteErpDTO> lotes = erpRestClient.get().uri(produtosPath).retrieve().body(LOTES_TYPE);

      return consolidarProdutos(lotes == null ? List.of() : lotes);
    } catch (RestClientException | IllegalArgumentException exception) {
      throw new ErpIntegrationException(
          "Não foi possível conectar com a API externa do ERP.", exception);
    }
  }

  List<ProdutoDTO> consolidarProdutos(List<LoteErpDTO> lotes) {
    Map<ChaveProdutoFilial, List<LoteErpDTO>> lotesAgrupados =
        lotes.stream()
            .collect(
                Collectors.groupingBy(this::criarChave, LinkedHashMap::new, Collectors.toList()));

    return lotesAgrupados.entrySet().stream()
        .map(entrada -> consolidarProduto(entrada.getKey(), entrada.getValue()))
        .toList();
  }

  private List<ProdutoDTO> aplicarFiltros(
      List<ProdutoDTO> produtos, String filial, String categoria, Boolean status) {
    return produtos.stream()
        .filter(produto -> filial == null || filial.equals(produto.filial()))
        .filter(produto -> categoria == null || categoria.equals(produto.categoria()))
        .filter(produto -> status == null || status.equals(produto.status()))
        .toList();
  }

  private ProdutoDTO consolidarProduto(ChaveProdutoFilial chave, List<LoteErpDTO> lotesDoProduto) {
    LoteErpDTO primeiroLote = lotesDoProduto.get(0);
    LoteErpDTO loteMaisRecente = encontrarLoteMaisRecente(lotesDoProduto);

    int estoqueAtual = somar(lotesDoProduto, LoteErpDTO::quantidade);
    int vendas7d = somar(lotesDoProduto, LoteErpDTO::vendas7d);
    int vendas30d = somar(lotesDoProduto, LoteErpDTO::vendas30d);

    return new ProdutoDTO(
        criarIdPublico(chave),
        chave.codigoProdutoErp(),
        primeiroLote.nomeProduto(),
        primeiroLote.categoria(),
        estoqueAtual,
        resolverConfiguracao(lotesDoProduto, LoteErpDTO::estoqueMinimo, "estoque_minimo", chave),
        vendas7d,
        vendas30d,
        calcularDiasValidade(lotesDoProduto),
        resolverConfiguracao(lotesDoProduto, LoteErpDTO::leadTimeDias, "lead_time_dias", chave),
        ErpValueParser.toBigDecimal(loteMaisRecente.preco()),
        ErpValueParser.toBigDecimal(loteMaisRecente.custo()),
        ErpValueParser.toInstant(loteMaisRecente.dataEntrada()),
        estoqueAtual > 0,
        primeiroLote.filial());
  }

  private ChaveProdutoFilial criarChave(LoteErpDTO lote) {
    return new ChaveProdutoFilial(lote.codigoProdutoErp(), lote.codigoFilialErp());
  }

  private String criarIdPublico(ChaveProdutoFilial chave) {
    return "%s:%s"
        .formatted(
            Objects.toString(chave.codigoProdutoErp(), ""),
            Objects.toString(chave.codigoFilialErp(), ""));
  }

  private int somar(List<LoteErpDTO> lotes, Function<LoteErpDTO, Object> campo) {
    return lotes.stream().map(campo).mapToInt(ErpValueParser::toIntegerOrZero).sum();
  }

  private Integer resolverConfiguracao(
      List<LoteErpDTO> lotes,
      Function<LoteErpDTO, Object> campo,
      String nomeCampo,
      ChaveProdutoFilial chave) {
    NavigableSet<Integer> valores =
        lotes.stream()
            .map(campo)
            .map(ErpValueParser::toInteger)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(TreeSet::new));

    if (valores.size() > MAX_VALUES_WITHOUT_DIVERGENCE && LOGGER.isWarnEnabled()) {
      LOGGER.warn(
          "Valores divergentes para {} no agrupamento {}:{}; usando o maior valor: {}",
          nomeCampo,
          chave.codigoProdutoErp(),
          chave.codigoFilialErp(),
          valores.last());
    }

    return valores.isEmpty() ? null : valores.last();
  }

  private Integer calcularDiasValidade(List<LoteErpDTO> lotes) {
    LocalDate validadeMaisProxima =
        lotes.stream()
            .filter(lote -> ErpValueParser.toIntegerOrZero(lote.quantidade()) > 0)
            .map(lote -> ErpValueParser.toLocalDate(lote.dataValidade()))
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(null);

    if (validadeMaisProxima == null) {
      return null;
    }

    return Math.toIntExact(ChronoUnit.DAYS.between(LocalDate.now(clock), validadeMaisProxima));
  }

  private LoteErpDTO encontrarLoteMaisRecente(List<LoteErpDTO> lotes) {
    Comparator<LoteErpDTO> comparator =
        Comparator.comparing(
                (LoteErpDTO lote) -> ErpValueParser.toLocalDate(lote.dataEntrada()),
                Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(lote -> Objects.toString(lote.id(), ""));

    return lotes.stream().max(comparator).orElseThrow();
  }

  private record ChaveProdutoFilial(String codigoProdutoErp, String codigoFilialErp) {}
}
