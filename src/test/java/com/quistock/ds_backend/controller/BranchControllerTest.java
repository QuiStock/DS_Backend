package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.handler.ApiExceptionHandler;
import com.quistock.ds_backend.model.dto.BranchDTO;
import com.quistock.ds_backend.service.BranchService;
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

class BranchControllerTest {

  @Test
  void shouldListBranchesUsingAuthenticatedUser() throws Exception {
    BranchService branchService = mock(BranchService.class);
    when(branchService.listBranches(1001L))
        .thenReturn(
            List.of(
                new BranchDTO("FIL001", 51L, "Santana Store", null, null, null, null, null, 3L)));

    mockMvc(branchService)
        .perform(get("/branches"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("FIL001"))
        .andExpect(jsonPath("$[0].store_id").value(51))
        .andExpect(jsonPath("$[0].region_id").value(3))
        .andExpect(jsonPath("$[0].name").value("Santana Store"));
  }

  @Test
  void shouldReturn503WhenErpIsUnavailable() throws Exception {
    BranchService branchService = mock(BranchService.class);
    when(branchService.listBranches(1001L))
        .thenThrow(
            new ErpIntegrationException(
                "Could not connect to the external ERP API.", new RuntimeException()));

    mockMvc(branchService)
        .perform(get("/branches"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("ERP_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Could not connect to the external ERP API."));
  }

  private MockMvc mockMvc(BranchService branchService) {
    return MockMvcBuilders.standaloneSetup(new BranchController(branchService))
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
                  ModelAndViewContainer mavContainer,
                  NativeWebRequest webRequest,
                  WebDataBinderFactory binderFactory) {
                return Jwt.withTokenValue("test-token")
                    .header("alg", "RS256")
                    .subject("1001")
                    .build();
              }
            })
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }
}
