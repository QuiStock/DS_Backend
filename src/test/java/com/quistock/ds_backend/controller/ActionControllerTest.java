package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.exception.FlowNotFoundException;
import com.quistock.ds_backend.handler.ApiExceptionHandler;
import com.quistock.ds_backend.model.dto.ActionDTO;
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

  private MockMvc mockMvc(ActionService actionService) {
    return MockMvcBuilders.standaloneSetup(new ActionController(actionService))
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }
}
