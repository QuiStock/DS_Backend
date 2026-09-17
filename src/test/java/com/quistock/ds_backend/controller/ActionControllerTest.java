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
import com.quistock.ds_backend.handler.ApiExceptionHandler;
import com.quistock.ds_backend.model.dto.ActionDTO;
import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.ActionStatusResponse;
import com.quistock.ds_backend.model.dto.GenerateActionsResponse;
import com.quistock.ds_backend.service.ActionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActionControllerTest {

  @Test
  void shouldGenerateActionsOnPublicRoute() throws Exception {
    ActionService actionService = mock(ActionService.class);
    ActionDTO action =
        new ActionDTO(
            "501", "PROMOTION", "SUGGESTED", "Product has expiration or excess stock risk.");
    when(actionService.generateActions("101"))
        .thenReturn(new GenerateActionsResponse("101", List.of(action)));

    mockMvc(actionService)
        .perform(
            post("/api/actions/generate")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flow_id\":\"101\"}"))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.flow_id").value("101"))
        .andExpect(jsonPath("$.generated_actions[0].id").value("501"))
        .andExpect(jsonPath("$.generated_actions[0].action_type").value("PROMOTION"))
        .andExpect(jsonPath("$.generated_actions[0].status").value("SUGGESTED"));
  }

  @Test
  void shouldReturn404WhenFlowIsNotFound() throws Exception {
    ActionService actionService = mock(ActionService.class);
    when(actionService.generateActions("UNKNOWN")).thenThrow(new FlowNotFoundException("UNKNOWN"));

    mockMvc(actionService)
        .perform(
            post("/api/actions/generate")
                .contextPath("/api")
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
            "SUGGESTED",
            "Product has expiration or excess stock risk.");
    when(actionService.listActions("SUGGESTED", "PROMOTION", "101")).thenReturn(List.of(action));

    mockMvc(actionService)
        .perform(
            get("/api/actions")
                .contextPath("/api")
                .queryParam("status", "SUGGESTED")
                .queryParam("action_type", "PROMOTION")
                .queryParam("flow_id", "101"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("501"))
        .andExpect(jsonPath("$[0].flow_id").value("101"))
        .andExpect(jsonPath("$[0].product_name").value("Whole Milk 1L"))
        .andExpect(jsonPath("$[0].action_type").value("PROMOTION"))
        .andExpect(jsonPath("$[0].status").value("SUGGESTED"));
  }

  @Test
  void shouldUpdateActionStatusOnPublicRoute() throws Exception {
    ActionService actionService = mock(ActionService.class);
    when(actionService.updateActionStatus("501", "APPROVED"))
        .thenReturn(new ActionStatusResponse("501", "APPROVED"));

    mockMvc(actionService)
        .perform(
            patch("/api/actions/501/status")
                .contextPath("/api")
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
    when(actionService.updateActionStatus("UNKNOWN", "APPROVED"))
        .thenThrow(new ActionNotFoundException("UNKNOWN"));

    mockMvc(actionService)
        .perform(
            patch("/api/actions/UNKNOWN/status")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("ACTION_NOT_FOUND"));
  }

  @Test
  void shouldReturn400ForUnsupportedActionStatus() throws Exception {
    ActionService actionService = mock(ActionService.class);
    when(actionService.updateActionStatus("501", "INVALID"))
        .thenThrow(new InvalidActionStatusException("INVALID"));

    mockMvc(actionService)
        .perform(
            patch("/api/actions/501/status")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INVALID\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
  }

  private MockMvc mockMvc(ActionService actionService) {
    return MockMvcBuilders.standaloneSetup(new ActionController(actionService))
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }
}
