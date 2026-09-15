package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.FluxoDTO;
import com.quistock.ds_backend.model.dto.ProdutoDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class FluxoService {
  private static final BigDecimal DAYS_IN_WEEK = BigDecimal.valueOf(7);
  private static final BigDecimal DAYS_IN_MONTH = BigDecimal.valueOf(30);
  private static final int DECIMAL_SCALE = 2;

  private final ProdutoService produtoService;
  private final Clock clock;
  private final AtomicLong nextId = new AtomicLong(100);
  private final List<FluxoDTO> fluxos = new CopyOnWriteArrayList<>();

  public FluxoService(ProdutoService service, Clock applicationClock) {
    this.produtoService = service;
    this.clock = applicationClock;
  }

  public FluxoDTO analisarProduto(String produtoId) {
    ProdutoDTO produto = produtoService.buscarProdutoPorId(produtoId);
    BigDecimal mediaVendasDiaria = calcularMediaVendasDiaria(produto);
    BigDecimal coberturaEstoqueDias = calcularCobertura(produto, mediaVendasDiaria);
    Classificacao classificacao = classificar(produto, coberturaEstoqueDias);

    FluxoDTO fluxo =
        new FluxoDTO(
            String.valueOf(nextId.incrementAndGet()),
            produto.id(),
            produto.nome(),
            classificacao.tipo(),
            "ANALISADO",
            classificacao.motivo(),
            mediaVendasDiaria,
            coberturaEstoqueDias,
            produto.diasValidade(),
            produto.leadTimeFornecedor(),
            Instant.now(clock));
    fluxos.add(fluxo);
    return fluxo;
  }

  public List<FluxoDTO> listarFluxos() {
    return List.copyOf(fluxos);
  }

  private BigDecimal calcularMediaVendasDiaria(ProdutoDTO produto) {
    int vendas7d = valorOuZero(produto.vendas7d());
    int vendas30d = valorOuZero(produto.vendas30d());
    if (vendas7d > 0) {
      return dividir(vendas7d, DAYS_IN_WEEK);
    }
    if (vendas30d > 0) {
      return dividir(vendas30d, DAYS_IN_MONTH);
    }
    return BigDecimal.ZERO.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal calcularCobertura(ProdutoDTO produto, BigDecimal mediaVendasDiaria) {
    if (mediaVendasDiaria.signum() == 0) {
      return BigDecimal.ZERO.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }
    return dividir(valorOuZero(produto.estoqueAtual()), mediaVendasDiaria);
  }

  private BigDecimal dividir(int valor, BigDecimal divisor) {
    return BigDecimal.valueOf(valor).divide(divisor, DECIMAL_SCALE, RoundingMode.HALF_UP);
  }

  private Classificacao classificar(ProdutoDTO produto, BigDecimal coberturaEstoqueDias) {
    Integer diasValidade = produto.diasValidade();
    if (diasValidade != null
        && BigDecimal.valueOf(diasValidade).compareTo(coberturaEstoqueDias) <= 0) {
      return new Classificacao("BAIXO", "Produto vencido ou próximo do vencimento.");
    }

    int estoqueAtual = valorOuZero(produto.estoqueAtual());
    if (coberturaEstoqueDias.signum() == 0 && estoqueAtual > 0) {
      return new Classificacao("BAIXO", "Produto sem vendas recentes e com estoque disponível.");
    }

    int estoqueMinimo = valorOuZero(produto.estoqueMinimo());
    int leadTime = valorOuZero(produto.leadTimeFornecedor());
    if (estoqueAtual <= 0
        || estoqueAtual < estoqueMinimo
        || coberturaEstoqueDias.compareTo(BigDecimal.valueOf(leadTime)) <= 0) {
      return new Classificacao(
          "ALTO", "Risco de ruptura: estoque abaixo do mínimo ou cobertura inferior ao lead time.");
    }

    return new Classificacao("MEDIO", "Estoque adequado para a demanda atual.");
  }

  private int valorOuZero(Integer valor) {
    return valor == null ? 0 : valor;
  }

  private record Classificacao(String tipo, String motivo) {}
}
