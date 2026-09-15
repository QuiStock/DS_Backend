package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.handler.ApiExceptionHandler;
import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.service.ProductService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerTest {

  @Test
  void shouldListProductsOnPublicRoute() throws Exception {
    ProductService productService = mock(ProductService.class);
    when(productService.listProducts(null, null, null)).thenReturn(List.of(product()));

    MockMvc mockMvc = createMockMvc(productService);

    mockMvc
        .perform(get("/api/products").contextPath("/api"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("PROD001:FIL001"))
        .andExpect(jsonPath("$[0].current_stock").value(71))
        .andExpect(jsonPath("$[0].batch_number").doesNotExist());
  }

  @Test
  void shouldReturn503WhenErpIsUnavailable() throws Exception {
    ProductService productService = mock(ProductService.class);
    when(productService.listProducts(null, null, null))
        .thenThrow(
            new ErpIntegrationException(
                "Could not connect to the external ERP API.", new RuntimeException()));

    MockMvc mockMvc = createMockMvc(productService);

    mockMvc
        .perform(get("/api/products").contextPath("/api"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("ERP_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Could not connect to the external ERP API."));
  }

  @Test
  void shouldFindProductByIdOnPublicRoute() throws Exception {
    ProductService productService = mock(ProductService.class);
    when(productService.findProductById("PROD001:FIL001")).thenReturn(product());

    MockMvc mockMvc = createMockMvc(productService);

    mockMvc
        .perform(get("/api/products/PROD001:FIL001").contextPath("/api"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("PROD001:FIL001"))
        .andExpect(jsonPath("$.sku").value("PROD001"));
  }

  @Test
  void shouldReturn404WhenProductIsNotFound() throws Exception {
    ProductService productService = mock(ProductService.class);
    when(productService.findProductById("UNKNOWN"))
        .thenThrow(new ProductNotFoundException("UNKNOWN"));

    MockMvc mockMvc = createMockMvc(productService);

    mockMvc
        .perform(get("/api/products/UNKNOWN").contextPath("/api"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Product was not found for the provided ID."));
  }

  private MockMvc createMockMvc(ProductService productService) {
    return MockMvcBuilders.standaloneSetup(new ProductController(productService))
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }

  private ProductDTO product() {
    return new ProductDTO(
        "PROD001:FIL001",
        "PROD001",
        "Whole Milk 1L",
        "Dairy",
        71,
        40,
        36,
        150,
        33,
        5,
        new BigDecimal("7.99"),
        new BigDecimal("5.20"),
        Instant.parse("2026-08-20T00:00:00Z"),
        true,
        "Santana Store");
  }
}
