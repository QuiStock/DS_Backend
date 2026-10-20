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
import com.quistock.ds_backend.repository.StoreScope;
import com.quistock.ds_backend.service.ProductService;
import com.quistock.ds_backend.service.UserAccessGuard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ProductControllerTest {
  private static final StoreScope SCOPE = new StoreScope(1001L, null, null);

  @Test
  void shouldListProductsOnPublicRoute() throws Exception {
    ProductService productService = mock(ProductService.class);
    when(productService.listProducts(null, null, null, SCOPE)).thenReturn(List.of(product()));

    MockMvc mockMvc = createMockMvc(productService);

    mockMvc
        .perform(get("/products"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("PROD001:FIL001"))
        .andExpect(jsonPath("$[0].current_stock").value(71))
        .andExpect(jsonPath("$[0].batch_number").doesNotExist());
  }

  @Test
  void shouldBindAllProductFiltersFromTheQueryString() throws Exception {
    ProductService productService = mock(ProductService.class);
    when(productService.listProducts("Santana Store", "Dairy", true, SCOPE))
        .thenReturn(List.of(product()));

    createMockMvc(productService)
        .perform(
            get("/products")
                .queryParam("branch", "Santana Store")
                .queryParam("category", "Dairy")
                .queryParam("status", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("PROD001:FIL001"));
  }

  @Test
  void shouldReturn503WhenErpIsUnavailable() throws Exception {
    ProductService productService = mock(ProductService.class);
    when(productService.listProducts(null, null, null, SCOPE))
        .thenThrow(
            new ErpIntegrationException(
                "Could not connect to the external ERP API.", new RuntimeException()));

    MockMvc mockMvc = createMockMvc(productService);

    mockMvc
        .perform(get("/products"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("ERP_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Could not connect to the external ERP API."));
  }

  @Test
  void shouldFindProductByIdOnPublicRoute() throws Exception {
    ProductService productService = mock(ProductService.class);
    when(productService.findProductById("PROD001:FIL001", SCOPE)).thenReturn(product());

    MockMvc mockMvc = createMockMvc(productService);

    mockMvc
        .perform(get("/products/PROD001:FIL001"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("PROD001:FIL001"))
        .andExpect(jsonPath("$.sku").value("PROD001"));
  }

  @Test
  void shouldReturn404WhenProductIsNotFound() throws Exception {
    ProductService productService = mock(ProductService.class);
    when(productService.findProductById("UNKNOWN", SCOPE))
        .thenThrow(new ProductNotFoundException("UNKNOWN"));

    MockMvc mockMvc = createMockMvc(productService);

    mockMvc
        .perform(get("/products/UNKNOWN"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Product was not found for the provided ID."));
  }

  @Test
  void shouldReturn503WhenErpIsUnavailableForProductLookup() throws Exception {
    ProductService productService = mock(ProductService.class);
    when(productService.findProductById("PROD001:FIL001", SCOPE))
        .thenThrow(
            new ErpIntegrationException(
                "Could not connect to the external ERP API.", new RuntimeException()));

    createMockMvc(productService)
        .perform(get("/products/PROD001:FIL001"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("ERP_UNAVAILABLE"));
  }

  @Test
  void shouldReturnContractErrorForInvalidStatusFilter() throws Exception {
    ProductService productService = mock(ProductService.class);

    createMockMvc(productService)
        .perform(get("/products").queryParam("status", "active"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Required fields are missing or invalid."));
  }

  private MockMvc createMockMvc(ProductService productService) {
    UserAccessGuard accessGuard = mock(UserAccessGuard.class);
    when(accessGuard.requireStoreScope(1001L, null)).thenReturn(SCOPE);
    return MockMvcBuilders.standaloneSetup(new ProductController(productService, accessGuard))
        .setCustomArgumentResolvers(
            new HandlerMethodArgumentResolver() {
              @Override
              public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && Jwt.class.isAssignableFrom(parameter.getParameterType());
              }

              @Override
              public Object resolveArgument(
                  MethodParameter parameter,
                  ModelAndViewContainer container,
                  NativeWebRequest request,
                  WebDataBinderFactory factory) {
                return Jwt.withTokenValue("test-token")
                    .header("alg", "RS256")
                    .subject("1001")
                    .build();
              }
            })
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
