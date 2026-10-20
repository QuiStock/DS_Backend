package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.repository.ProductRepository;
import com.quistock.ds_backend.repository.StoreScope;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductServiceTest {
  private static final StoreScope SCOPE = new StoreScope(1001L, null, null);
  private ProductRepository productRepository;
  private ErpSyncService erpSyncService;
  private ProductService productService;

  @BeforeEach
  void setUp() {
    productRepository = mock(ProductRepository.class);
    erpSyncService = mock(ErpSyncService.class);
    productService = new ProductService(productRepository, Optional.of(erpSyncService));
  }

  @Test
  void shouldListProductsFromThePersistedProjection() {
    ProductDTO product = product("PROD001:FIL001");
    when(productRepository.hasSnapshot()).thenReturn(true);
    when(productRepository.findAll("Santana Store", "Dairy", true, SCOPE))
        .thenReturn(List.of(product));

    assertThat(productService.listProducts("Santana Store", "Dairy", true, SCOPE))
        .containsExactly(product);
    verify(erpSyncService, never()).syncNow();
  }

  @Test
  void shouldSynchronizeBeforeReadingAnEmptyCatalog() {
    ProductDTO product = product("PROD001:FIL001");
    when(productRepository.hasSnapshot()).thenReturn(false);
    when(erpSyncService.hasCompletedSync()).thenReturn(false);
    when(productRepository.findAll(null, null, null, SCOPE)).thenReturn(List.of(product));

    assertThat(productService.listProducts(null, null, null, SCOPE)).containsExactly(product);

    verify(erpSyncService).syncNow();
  }

  @Test
  void shouldSkipSynchronizationWhenItHasAlreadyCompleted() {
    when(productRepository.hasSnapshot()).thenReturn(false);
    when(erpSyncService.hasCompletedSync()).thenReturn(true);
    when(productRepository.findAll(null, null, null, SCOPE)).thenReturn(List.of());

    assertThat(productService.listProducts(null, null, null, SCOPE)).isEmpty();

    verify(erpSyncService, never()).syncNow();
  }

  @Test
  void shouldSkipSynchronizationWhenTheOptionalSyncServiceIsDisabled() {
    ProductService serviceWithoutSync = new ProductService(productRepository, Optional.empty());
    when(productRepository.findAll(null, null, null, SCOPE)).thenReturn(List.of());

    assertThat(serviceWithoutSync.listProducts(null, null, null, SCOPE)).isEmpty();

    verify(productRepository).findAll(null, null, null, SCOPE);
  }

  @Test
  void shouldFindProductByPublicIdentifier() {
    ProductDTO product = product("PROD001:FIL001");
    when(productRepository.hasSnapshot()).thenReturn(true);
    when(productRepository.findByPublicId(product.id(), SCOPE)).thenReturn(Optional.of(product));

    assertThat(productService.findProductById(product.id(), SCOPE)).isEqualTo(product);
  }

  @Test
  void shouldReportProductNotFoundWhenIdentifierIsMissing() {
    when(productRepository.hasSnapshot()).thenReturn(true);

    assertThatThrownBy(() -> productService.findProductById("UNKNOWN", SCOPE))
        .isInstanceOf(ProductNotFoundException.class);
  }

  private ProductDTO product(String id) {
    return new ProductDTO(
        id,
        "PROD001",
        "Whole Milk 1L",
        "Dairy",
        100,
        40,
        14,
        60,
        30,
        5,
        new BigDecimal("7.99"),
        new BigDecimal("5.20"),
        Instant.parse("2026-08-20T00:00:00Z"),
        true,
        "Santana Store");
  }
}
