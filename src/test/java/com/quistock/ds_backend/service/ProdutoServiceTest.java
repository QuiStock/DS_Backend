package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.model.dto.LoteErpDTO;
import com.quistock.ds_backend.model.dto.ProdutoDTO;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProdutoServiceTest {

  private static final Clock TEST_CLOCK =
      Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC);

  private MockRestServiceServer server;
  private ProdutoService produtoService;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://erp.test");
    server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();
    produtoService = new ProdutoService(restClient, "/produto", TEST_CLOCK);
  }

  @Test
  void umLoteDeveGerarUmProdutoConsolidado() {
    LoteErpDTO lote =
        lote(
            "1",
            "PROD001",
            "FIL001",
            "Loja Santana",
            "2026-08-20",
            "2026-09-30",
            "71",
            "7.99",
            "5.20",
            "36",
            "150",
            "40",
            "5");

    ProdutoDTO produto = produtoService.consolidarProdutos(List.of(lote)).get(0);

    assertThat(produto.id()).isEqualTo("PROD001:FIL001");
    assertThat(produto.sku()).isEqualTo("PROD001");
    assertThat(produto.nome()).isEqualTo("Leite Integral 1L");
    assertThat(produto.categoria()).isEqualTo("Laticinios");
    assertThat(produto.estoqueAtual()).isEqualTo(71);
    assertThat(produto.estoqueMinimo()).isEqualTo(40);
    assertThat(produto.vendas7d()).isEqualTo(36);
    assertThat(produto.vendas30d()).isEqualTo(150);
    assertThat(produto.diasValidade()).isEqualTo(33);
    assertThat(produto.leadTimeFornecedor()).isEqualTo(5);
    assertThat(produto.preco()).isEqualByComparingTo("7.99");
    assertThat(produto.custo()).isEqualByComparingTo("5.20");
    assertThat(produto.ultimaReposicao()).isEqualTo(Instant.parse("2026-08-20T00:00:00Z"));
    assertThat(produto.status()).isTrue();
    assertThat(produto.filial()).isEqualTo("Loja Santana");
  }

  @Test
  void lotesDoMesmoProdutoEFilialDevemSerSomados() {
    List<LoteErpDTO> lotes =
        List.of(
            lote(
                "1",
                "PROD001",
                "FIL001",
                "Loja Santana",
                "2026-08-20",
                "2026-09-30",
                50,
                7.99,
                5.20,
                10,
                100,
                40,
                5),
            lote(
                "2",
                "PROD001",
                "FIL001",
                "Loja Santana",
                "2026-08-21",
                "2026-09-10",
                40,
                8.10,
                5.30,
                20,
                100,
                40,
                5),
            lote(
                "3",
                "PROD001",
                "FIL001",
                "Loja Santana",
                "2026-08-22",
                "2026-09-20",
                30,
                8.20,
                5.40,
                20,
                0,
                40,
                5));

    ProdutoDTO produto = produtoService.consolidarProdutos(lotes).get(0);

    assertThat(produtoService.consolidarProdutos(lotes)).hasSize(1);
    assertThat(produto.estoqueAtual()).isEqualTo(120);
    assertThat(produto.vendas7d()).isEqualTo(50);
    assertThat(produto.vendas30d()).isEqualTo(200);
    assertThat(produto.estoqueMinimo()).isEqualTo(40);
    assertThat(produto.leadTimeFornecedor()).isEqualTo(5);
    assertThat(produto.diasValidade()).isEqualTo(13);
  }

  @Test
  void mesmoProdutoEmFiliaisDiferentesNaoDeveSerAgrupado() {
    List<ProdutoDTO> produtos =
        produtoService.consolidarProdutos(
            List.of(
                lote(
                    "1",
                    "PROD001",
                    "FIL001",
                    "Loja Santana",
                    "2026-08-20",
                    "2026-09-30",
                    50,
                    7.99,
                    5.20,
                    10,
                    20,
                    40,
                    5),
                lote(
                    "2",
                    "PROD001",
                    "FIL002",
                    "Loja Centro",
                    "2026-08-20",
                    "2026-09-30",
                    70,
                    7.99,
                    5.20,
                    10,
                    20,
                    40,
                    5)));

    assertThat(produtos).hasSize(2);
    assertThat(produtos)
        .extracting(ProdutoDTO::id)
        .containsExactly("PROD001:FIL001", "PROD001:FIL002");
    assertThat(produtos).extracting(ProdutoDTO::estoqueAtual).containsExactly(50, 70);
  }

  @Test
  void loteSemEstoqueNaoDeveDefinirValidadePrincipal() {
    ProdutoDTO produto =
        produtoService
            .consolidarProdutos(
                List.of(
                    lote(
                        "1",
                        "PROD001",
                        "FIL001",
                        "Loja Santana",
                        "2026-08-20",
                        "2026-08-20",
                        0,
                        7.99,
                        5.20,
                        0,
                        0,
                        40,
                        5),
                    lote(
                        "2",
                        "PROD001",
                        "FIL001",
                        "Loja Santana",
                        "2026-08-21",
                        "2026-09-20",
                        10,
                        7.99,
                        5.20,
                        1,
                        2,
                        40,
                        5)))
            .get(0);

    assertThat(produto.diasValidade()).isEqualTo(23);
  }

  @Test
  void precoECustoDevemVirDoLoteMaisRecente() {
    ProdutoDTO produto =
        produtoService
            .consolidarProdutos(
                List.of(
                    lote(
                        "1",
                        "PROD001",
                        "FIL001",
                        "Loja Santana",
                        "2026-08-20",
                        "2026-09-30",
                        50,
                        7.99,
                        5.20,
                        10,
                        20,
                        40,
                        5),
                    lote(
                        "2",
                        "PROD001",
                        "FIL001",
                        "Loja Santana",
                        "2026-08-25",
                        "2026-10-30",
                        40,
                        8.49,
                        5.80,
                        10,
                        20,
                        40,
                        5)))
            .get(0);

    assertThat(produto.preco()).isEqualByComparingTo("8.49");
    assertThat(produto.custo()).isEqualByComparingTo("5.80");
    assertThat(produto.ultimaReposicao()).isEqualTo(Instant.parse("2026-08-25T00:00:00Z"));
  }

  @Test
  void dataUnixDeveSerConvertidaParaDataDeValidade() {
    long timestamp = Instant.parse("2026-09-15T00:00:00Z").getEpochSecond();
    LoteErpDTO lote =
        lote(
            "1",
            "PROD001",
            "FIL001",
            "Loja Santana",
            "2026-08-20",
            timestamp,
            10,
            7.99,
            5.20,
            1,
            2,
            40,
            5);

    ProdutoDTO produto = produtoService.consolidarProdutos(List.of(lote)).get(0);

    assertThat(produto.diasValidade()).isEqualTo(18);
  }

  @Test
  void filtrosDevemSerAplicadosDepoisDaConsolidacao() {
    configurarRespostaDoErp(
        """
        [
          {
            "id": "1",
            "codigo_produto_erp": "PROD001",
            "nome_produto": "Leite Integral 1L",
            "categoria": "Laticinios",
            "unidade_medida": "UN",
            "num_lote": "LT001",
            "data_validade": "2026-09-30",
            "quantidade": "50",
            "preco": "7.99",
            "custo": "5.20",
            "codigo_filial_erp": "FIL001",
            "filial": "Loja Santana",
            "certificado_qualidade": true,
            "data_entrada": "2026-08-20",
            "vendas_7d": "10",
            "vendas_30d": "20",
            "estoque_minimo": "40",
            "lead_time_dias": "5"
          },
          {
            "id": "2",
            "codigo_produto_erp": "PROD001",
            "nome_produto": "Leite Integral 1L",
            "categoria": "Laticinios",
            "unidade_medida": "UN",
            "num_lote": "LT002",
            "data_validade": "2026-10-30",
            "quantidade": "20",
            "preco": "8.19",
            "custo": "5.40",
            "codigo_filial_erp": "FIL001",
            "filial": "Loja Santana",
            "certificado_qualidade": true,
            "data_entrada": "2026-08-21",
            "vendas_7d": "5",
            "vendas_30d": "10",
            "estoque_minimo": "40",
            "lead_time_dias": "5"
          },
          {
            "id": "3",
            "codigo_produto_erp": "PROD002",
            "nome_produto": "Refrigerante",
            "categoria": "Bebidas",
            "unidade_medida": "UN",
            "num_lote": "LT003",
            "data_validade": "2026-10-30",
            "quantidade": "0",
            "preco": "5.99",
            "custo": "3.20",
            "codigo_filial_erp": "FIL002",
            "filial": "Loja Centro",
            "certificado_qualidade": true,
            "data_entrada": "2026-08-22",
            "vendas_7d": "0",
            "vendas_30d": "0",
            "estoque_minimo": "20",
            "lead_time_dias": "3"
          }
        ]
        """);

    assertThat(produtoService.listarProdutos("Loja Santana", null, null))
        .extracting(ProdutoDTO::id)
        .containsExactly("PROD001:FIL001");
    assertThat(produtoService.listarProdutos(null, "Bebidas", null))
        .extracting(ProdutoDTO::id)
        .containsExactly("PROD002:FIL002");
    assertThat(produtoService.listarProdutos(null, null, false))
        .extracting(ProdutoDTO::id)
        .containsExactly("PROD002:FIL002");
  }

  @Test
  void configuracoesDivergentesDevemUsarMaiorValorDeFormaDeterministica() {
    ProdutoDTO produto =
        produtoService
            .consolidarProdutos(
                List.of(
                    lote(
                        "1",
                        "PROD001",
                        "FIL001",
                        "Loja Santana",
                        "2026-08-20",
                        "2026-09-30",
                        50,
                        7.99,
                        5.20,
                        10,
                        20,
                        40,
                        5),
                    lote(
                        "2",
                        "PROD001",
                        "FIL001",
                        "Loja Santana",
                        "2026-08-21",
                        "2026-09-30",
                        40,
                        7.99,
                        5.20,
                        10,
                        20,
                        50,
                        7)))
            .get(0);

    assertThat(produto.estoqueMinimo()).isEqualTo(50);
    assertThat(produto.leadTimeFornecedor()).isEqualTo(7);
  }

  @Test
  void falhaNoErpDeveSerConvertidaEmExcecaoDeIntegracao() {
    server
        .expect(requestTo("http://erp.test/produto"))
        .andExpect(method(GET))
        .andRespond(withServerError());

    assertThatThrownBy(() -> produtoService.listarProdutos(null, null, null))
        .isInstanceOf(ErpIntegrationException.class)
        .hasMessage("Não foi possível conectar com a API externa do ERP.");
  }

  private void configurarRespostaDoErp(String resposta) {
    server
        .expect(ExpectedCount.times(3), requestTo("http://erp.test/produto"))
        .andExpect(method(GET))
        .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));
  }

  private LoteErpDTO lote(
      String id,
      String codigoProduto,
      String codigoFilial,
      String filial,
      Object dataEntrada,
      Object dataValidade,
      Object quantidade,
      Object preco,
      Object custo,
      Object vendas7d,
      Object vendas30d,
      Object estoqueMinimo,
      Object leadTime) {
    return new LoteErpDTO(
        id,
        codigoProduto,
        "Leite Integral 1L",
        "Laticinios",
        "UN",
        "LT" + id,
        dataValidade,
        quantidade,
        preco,
        custo,
        codigoFilial,
        filial,
        true,
        dataEntrada,
        vendas7d,
        vendas30d,
        estoqueMinimo,
        leadTime);
  }
}
