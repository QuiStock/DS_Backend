package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.repository.FlowRepository;
import com.quistock.ds_backend.repository.StoreScope;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FlowServiceTest {
  private static final StoreScope SCOPE = new StoreScope(1001L, 1L, null);
  private static final Clock TEST_CLOCK =
      Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);
  private final FlowAnalysisCalculator calculator = new FlowAnalysisCalculator();
  private ProductService products;
  private FlowRepository repository;
  private FlowService service;

  @BeforeEach
  void setUp() {
    products = mock(ProductService.class);
    repository = mock(FlowRepository.class);
    service =
        new FlowService(
            products,
            repository,
            calculator,
            TEST_CLOCK,
            mock(com.quistock.ds_backend.repository.ErpProjectionLock.class));
  }

  @Test
  void shouldClassifySalesIndependentlyFromInventoryRisk() {
    var peers =
        List.of(
            BigDecimal.ZERO,
            BigDecimal.ONE,
            BigDecimal.TEN,
            new BigDecimal("20"),
            new BigDecimal("30"));
    assertThat(calculator.calculate(product(30, 100, 3), peers).flowType()).isEqualTo("HIGH");
    assertThat(calculator.calculate(product(10, 100, 3), peers).flowType()).isEqualTo("MEDIUM");
    assertThat(calculator.calculate(product(0, 100, 3), peers).flowType()).isEqualTo("LOW");
    assertThat(calculator.calculate(product(30, 100, 3), peers).risks())
        .contains("NEAR_EXPIRY", "EXPIRATION");
  }

  @Test
  void shouldKeepPercentileBoundariesInMedium() {
    var peers =
        List.of(
            BigDecimal.ONE,
            new BigDecimal("2"),
            new BigDecimal("3"),
            new BigDecimal("4"),
            new BigDecimal("5"));
    assertThat(calculator.calculate(product(2, 100, 30), peers).flowType()).isEqualTo("MEDIUM");
    assertThat(calculator.calculate(product(4, 100, 30), peers).flowType()).isEqualTo("MEDIUM");
    assertThat(calculator.calculate(product(1, 100, 30), peers).flowType()).isEqualTo("LOW");
  }

  @Test
  void shouldRequireEnoughComparableDataAndNeverTreatMissingSalesAsZero() {
    assertThat(calculator.calculate(product(0, 100, 30), List.of(BigDecimal.ZERO)).flowType())
        .isNull();
    var peers = java.util.Collections.nCopies(5, BigDecimal.TEN);
    assertThat(calculator.calculate(product(null, 100, 30), peers).flowType()).isNull();
    assertThat(calculator.calculate(product(null, 100, 30), peers).dailySalesAverage()).isNull();
    assertThat(calculator.calculate(product(10, 100, 30), peers).flowType()).isEqualTo("MEDIUM");
  }

  @Test
  void shouldSeparateExpiredNearExpiryAndStockout() {
    var peers = java.util.Collections.nCopies(5, BigDecimal.TEN);
    assertThat(calculator.calculate(product(10, 0, 3), peers).risks()).containsExactly("STOCKOUT");
    assertThat(calculator.calculate(product(10, 100, -1), peers).risks())
        .containsExactly("EXPIRED");
    assertThat(calculator.calculate(product(0, 100, 7), peers).risks())
        .contains("EXPIRATION", "NEAR_EXPIRY");
    assertThat(calculator.calculate(product(0, 100, 8), peers).risks())
        .doesNotContain("NEAR_EXPIRY");
    assertThat(calculator.calculate(product(10, 100, null), peers).risks()).isEmpty();
  }

  @Test
  void shouldPersistNewAnalysisAndReadItsServerMetadata() {
    var product = product(10, 100, 20);
    when(products.findProductById(product.id(), SCOPE)).thenReturn(product);
    when(repository.comparableSales(product.id(), SCOPE))
        .thenReturn(java.util.Collections.nCopies(5, BigDecimal.TEN));
    when(repository.insertAnalysis(any(), eq(SCOPE))).thenReturn(101L);
    when(repository.findById("101", SCOPE)).thenReturn(Optional.of(flow()));
    assertThat(service.analyzeProduct(product.id(), SCOPE)).isEqualTo(flow());
    var captor = ArgumentCaptor.forClass(FlowRepository.AnalysisSnapshot.class);
    verify(repository).insertAnalysis(captor.capture(), eq(SCOPE));
    assertThat(captor.getValue().flowType()).isEqualTo("MEDIUM");
    assertThat(captor.getValue().createdAt()).isEqualTo(TEST_CLOCK.instant());
  }

  @Test
  void shouldRejectMissingProduct() {
    when(products.findProductById("missing", SCOPE))
        .thenThrow(new ProductNotFoundException("missing"));
    assertThatThrownBy(() -> service.analyzeProduct("missing", SCOPE))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void shouldKeepHistoryAndFilterCurrentOnTheServer() {
    when(repository.findAll(null, null, null, SCOPE)).thenReturn(List.of(flow()));
    assertThat(service.listFlows(SCOPE)).containsExactly(flow());
    assertThat(service.currentFlows(null, SCOPE)).containsExactly(flow());
    assertThatThrownBy(() -> service.listFlows("invalid", null, null, SCOPE))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.listFlows(null, null, "invalid", SCOPE))
        .isInstanceOf(InvalidRequestException.class);
  }

  private ProductDTO product(Integer sales, Integer stock, Integer expiration) {
    return new ProductDTO(
        "PROD001:FIL001",
        "PROD001",
        "Milk",
        "Dairy",
        stock,
        5,
        sales,
        30,
        expiration,
        2,
        BigDecimal.TEN,
        BigDecimal.ONE,
        TEST_CLOCK.instant(),
        true,
        "Store");
  }

  private FlowDTO flow() {
    return new FlowDTO(
        "101",
        "PROD001:FIL001",
        "Milk",
        "MEDIUM",
        "ANALYZED",
        "reason",
        BigDecimal.ONE,
        BigDecimal.TEN,
        20,
        2,
        TEST_CLOCK.instant(),
        List.of(),
        "SALES_PERCENTILES_V1",
        true,
        1L);
  }
}
