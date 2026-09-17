package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.model.dto.BranchDTO;
import com.quistock.ds_backend.model.dto.ProductDTO;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BranchServiceTest {
  private ProductService productService;
  private BranchService branchService;

  @BeforeEach
  void setUp() {
    productService = mock(ProductService.class);
    branchService = new BranchService(productService);
  }

  @Test
  void shouldListUniqueBranchesFromProducts() {
    when(productService.listProducts(null, null, null))
        .thenReturn(
            List.of(
                product("PROD001:FIL001", "Santana Store"),
                product("PROD002:FIL001", "Santana Store"),
                product("PROD003:FIL002", "Downtown Store")));

    List<BranchDTO> branches = branchService.listBranches();

    assertThat(branches).hasSize(2);
    assertThat(branches.get(0))
        .isEqualTo(new BranchDTO("FIL001", "Santana Store", null, null, null, null, null));
    assertThat(branches.get(1))
        .isEqualTo(new BranchDTO("FIL002", "Downtown Store", null, null, null, null, null));
  }

  @Test
  void shouldReturnEmptyListWhenThereAreNoProducts() {
    when(productService.listProducts(null, null, null)).thenReturn(List.of());

    assertThat(branchService.listBranches()).isEmpty();
  }

  private ProductDTO product(String id, String branch) {
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
        branch);
  }
}
