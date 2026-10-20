package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.exception.ActionNotFoundException;
import com.quistock.ds_backend.exception.FlowNotFoundException;
import com.quistock.ds_backend.exception.InvalidActionStatusException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.handler.ApiExceptionHandler;
import com.quistock.ds_backend.model.dto.ActionDTO;
import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.ActionStatusResponse;
import com.quistock.ds_backend.model.dto.GenerateActionsResponse;
import com.quistock.ds_backend.model.dto.UpdateActionStatusRequest;
import com.quistock.ds_backend.repository.StoreScope;
import com.quistock.ds_backend.service.ActionService;
import com.quistock.ds_backend.service.UserAccessGuard;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ActionControllerTest {
  private static final StoreScope SCOPE = new StoreScope(1001L, null, null);
  private static final long ACTOR_ID = 1001L;
  private static final String JWT_REQUEST_ATTRIBUTE = "authenticatedJwt";

  @Test
  void shouldGenerateActionsUsingAuthenticatedUser() throws Exception {
    ActionService actionService = mock(ActionService.class);
    ActionDTO action =
        new ActionDTO(
            "501", "PROMOTION", "GENERATED", "Product has expiration or excess stock risk.");
    when(actionService.generateActions("101", null, null, SCOPE))
        .thenReturn(new GenerateActionsResponse("101", List.of(action)));

    mockMvc(actionService)
        .perform(
            post("/actions/generate")
                .with(testUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flow_id\":\"101\"}"))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.flow_id").value("101"))
        .andExpect(jsonPath("$.generated_actions[0].id").value("501"))
        .andExpect(jsonPath("$.generated_actions[0].action_type").value("PROMOTION"))
        .andExpect(jsonPath("$.generated_actions[0].status").value("GENERATED"));
  }

  @Test
  void shouldReturnContractErrorWhenFlowIdIsMissing() throws Exception {
    ActionService actionService = mock(ActionService.class);

    mockMvc(actionService)
        .perform(post("/actions/generate").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Required fields are missing or invalid."));
  }

  @Test
  void shouldReturn404WhenFlowIsNotFound() throws Exception {
    ActionService actionService = mock(ActionService.class);
    when(actionService.generateActions("UNKNOWN", null, null, SCOPE))
        .thenThrow(new FlowNotFoundException("UNKNOWN"));

    mockMvc(actionService)
        .perform(
            post("/actions/generate")
                .with(testUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flow_id\":\"UNKNOWN\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("FLOW_NOT_FOUND"));
  }

  @Test
  void shouldListActionsUsingContractFilters() throws Exception {
    ActionService actionService = mock(ActionService.class);
    ActionListItemDTO action =
        new ActionListItemDTO(
            "501",
            "101",
            "Whole Milk 1L",
            "PROMOTION",
            "GENERATED",
            "Product has expiration or excess stock risk.");
    when(actionService.listActions("GENERATED", "PROMOTION", "101", SCOPE))
        .thenReturn(List.of(action));

    mockMvc(actionService)
        .perform(
            get("/actions")
                .queryParam("status", "GENERATED")
                .queryParam("action_type", "PROMOTION")
                .queryParam("flow_id", "101"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("501"))
        .andExpect(jsonPath("$[0].flow_id").value("101"))
        .andExpect(jsonPath("$[0].product_name").value("Whole Milk 1L"))
        .andExpect(jsonPath("$[0].action_type").value("PROMOTION"))
        .andExpect(jsonPath("$[0].status").value("GENERATED"));
  }

  @Test
  void shouldReturn400ForUnsupportedActionTypeFilter() throws Exception {
    ActionService actionService = mock(ActionService.class);
    when(actionService.listActions(null, "DISCOUNT", null, SCOPE))
        .thenThrow(new InvalidRequestException());

    mockMvc(actionService)
        .perform(get("/actions").queryParam("action_type", "DISCOUNT"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Required fields are missing or invalid."));
  }

  @Test
  void shouldReturn400ForUnsupportedActionStatusFilter() throws Exception {
    ActionService actionService = mock(ActionService.class);
    when(actionService.listActions("ACTIVE", null, null, SCOPE))
        .thenThrow(new InvalidRequestException());

    mockMvc(actionService)
        .perform(get("/actions").queryParam("status", "ACTIVE"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Required fields are missing or invalid."));
  }

  @Test
  void shouldUpdateActionStatusUsingAuthenticatedUser() throws Exception {
    ActionService actionService = mock(ActionService.class);
    when(actionService.updateActionStatus("501", new UpdateActionStatusRequest("APPROVED"), SCOPE))
        .thenReturn(new ActionStatusResponse("501", "APPROVED"));

    mockMvc(actionService)
        .perform(
            patch("/actions/501/status")
                .with(testUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\"}"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("501"))
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void shouldReturn404WhenActionIsNotFound() throws Exception {
    ActionService actionService = mock(ActionService.class);
    when(actionService.updateActionStatus(
            "UNKNOWN", new UpdateActionStatusRequest("APPROVED"), SCOPE))
        .thenThrow(new ActionNotFoundException("UNKNOWN"));

    mockMvc(actionService)
        .perform(
            patch("/actions/UNKNOWN/status")
                .with(testUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("ACTION_NOT_FOUND"));
  }

  @Test
  void shouldReturn400ForUnsupportedActionStatus() throws Exception {
    ActionService actionService = mock(ActionService.class);
    when(actionService.updateActionStatus("501", new UpdateActionStatusRequest("INVALID"), SCOPE))
        .thenThrow(new InvalidActionStatusException("INVALID"));

    mockMvc(actionService)
        .perform(
            patch("/actions/501/status")
                .with(testUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INVALID\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Required fields are missing or invalid."));
  }

  @Test
  void shouldReturnContractErrorWhenActionStatusIsBlank() throws Exception {
    ActionService actionService = mock(ActionService.class);

    mockMvc(actionService)
        .perform(
            patch("/actions/501/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Required fields are missing or invalid."));
  }

  private MockMvc mockMvc(ActionService actionService) {
    UserAccessGuard accessGuard = mock(UserAccessGuard.class);
    when(accessGuard.requireStoreScope(1001L, null)).thenReturn(SCOPE);
    return MockMvcBuilders.standaloneSetup(new ActionController(actionService, accessGuard))
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
                Object token =
                    webRequest.getAttribute(JWT_REQUEST_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
                return token == null
                    ? Jwt.withTokenValue("test-token")
                        .header("alg", "RS256")
                        .subject("1001")
                        .build()
                    : token;
              }
            })
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }

  private RequestPostProcessor testUser() {
    Jwt token =
        Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject(Long.toString(ACTOR_ID))
            .claim("email", "mobile@test.example")
            .build();
    return request -> {
      request.setAttribute(JWT_REQUEST_ATTRIBUTE, token);
      return request;
    };
  }
}
