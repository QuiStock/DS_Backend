package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.model.dto.FluxoDTO;
import com.quistock.ds_backend.model.dto.ProdutoDTO;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FluxoServiceTest {
  private static final Clock TEST_CLOCK =
      Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC);

  private ProdutoService produtoService;
  private FluxoService fluxoService;

  @BeforeEach
  void setUp() {
    produtoService = mock(ProdutoService.class);
    fluxoService = new FluxoService(produtoService, TEST_CLOCK);
  }

  @Test
  void deveClassificarProdutoProximoDoVencimentoComoBaixo() {
    ProdutoDTO produto = produto(51, 58, 36, 150, 0, 3);
    when(produtoService.buscarProdutoPorId(produto.id())).thenReturn(produto);

    FluxoDTO fluxo = fluxoService.analisarProduto(produto.id());

    assertThat(fluxo.id()).isEqualTo("101");
    assertThat(fluxo.tipoFluxo()).isEqualTo("BAIXO");
    assertThat(fluxo.status()).isEqualTo("ANALISADO");
    assertThat(fluxo.motivo()).isEqualTo("Produto vencido ou próximo do vencimento.");
    assertThat(fluxo.mediaVendasDiaria()).isEqualByComparingTo("5.14");
    assertThat(fluxo.coberturaEstoqueDias()).isEqualByComparingTo("9.92");
    assertThat(fluxo.dataAnalise()).isEqualTo(TEST_CLOCK.instant());
  }

  @Test
  void deveClassificarProdutoComRiscoDeRupturaComoAlto() {
    ProdutoDTO produto = produto(5, 40, 14, 60, 30, 5);
    when(produtoService.buscarProdutoPorId(produto.id())).thenReturn(produto);

    FluxoDTO fluxo = fluxoService.analisarProduto(produto.id());

    assertThat(fluxo.tipoFluxo()).isEqualTo("ALTO");
    assertThat(fluxo.coberturaEstoqueDias()).isEqualByComparingTo("2.50");
  }

  @Test
  void deveClassificarProdutoAdequadoComoMedio() {
    ProdutoDTO produto = produto(100, 40, 14, 60, 365, 5);
    when(produtoService.buscarProdutoPorId(produto.id())).thenReturn(produto);

    FluxoDTO fluxo = fluxoService.analisarProduto(produto.id());

    assertThat(fluxo.tipoFluxo()).isEqualTo("MEDIO");
    assertThat(fluxo.motivo()).isEqualTo("Estoque adequado para a demanda atual.");
  }

  @Test
  void deveUsarMediaDeTrintaDiasQuandoNaoHaVendasNaSemana() {
    ProdutoDTO produto = produto(100, 40, 0, 60, null, 5);
    when(produtoService.buscarProdutoPorId(produto.id())).thenReturn(produto);

    FluxoDTO fluxo = fluxoService.analisarProduto(produto.id());

    assertThat(fluxo.mediaVendasDiaria()).isEqualByComparingTo("2.00");
    assertThat(fluxo.coberturaEstoqueDias()).isEqualByComparingTo("50.00");
  }

  @Test
  void deveClassificarEstoqueSemVendasComoBaixo() {
    ProdutoDTO produto = produto(100, 40, 0, 0, null, 5);
    when(produtoService.buscarProdutoPorId(produto.id())).thenReturn(produto);

    FluxoDTO fluxo = fluxoService.analisarProduto(produto.id());

    assertThat(fluxo.tipoFluxo()).isEqualTo("BAIXO");
    assertThat(fluxo.coberturaEstoqueDias()).isEqualByComparingTo("0.00");
  }

  @Test
  void deveListarFluxosGerados() {
    ProdutoDTO produto = produto(100, 40, 14, 60, 30, 5);
    when(produtoService.buscarProdutoPorId(produto.id())).thenReturn(produto);

    fluxoService.analisarProduto(produto.id());

    assertThat(fluxoService.listarFluxos()).hasSize(1);
  }

  private ProdutoDTO produto(
      Integer estoqueAtual,
      Integer estoqueMinimo,
      Integer vendas7d,
      Integer vendas30d,
      Integer diasValidade,
      Integer leadTime) {
    return new ProdutoDTO(
        "PROD001:FIL001",
        "PROD001",
        "Leite Integral 1L",
        "Laticinios",
        estoqueAtual,
        estoqueMinimo,
        vendas7d,
        vendas30d,
        diasValidade,
        leadTime,
        new BigDecimal("7.99"),
        new BigDecimal("5.20"),
        Instant.parse("2026-08-20T00:00:00Z"),
        true,
        "Loja Santana");
  }
}
