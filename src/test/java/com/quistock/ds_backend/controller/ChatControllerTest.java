package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.model.dto.ChatReferencedDataDTO;
import com.quistock.ds_backend.model.dto.ChatResponse;
import com.quistock.ds_backend.service.ChatService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatControllerTest {

  @Test
  void shouldAnswerChatOnPublicRoute() throws Exception {
    ChatService chatService = mock(ChatService.class);
    ChatResponse response =
        new ChatResponse(
            "There are products at risk of expiration. The main suggestion is to create a promotion for Whole Milk 1L.",
            "operations_agent",
            List.of(
                new ChatReferencedDataDTO("PROD001:FIL001", "Whole Milk 1L", "LOW", "PROMOTION")));
    when(chatService.answer(org.mockito.ArgumentMatchers.any())).thenReturn(response);

    mockMvc(chatService)
        .perform(
            post("/api/chat")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"user_id\":\"1\",\"message\":\"Which products need a promotion?\"}"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.answer").value(response.answer()))
        .andExpect(jsonPath("$.responsible_agent").value("operations_agent"))
        .andExpect(jsonPath("$.referenced_data[0].product_id").value("PROD001:FIL001"))
        .andExpect(jsonPath("$.referenced_data[0].name").value("Whole Milk 1L"))
        .andExpect(jsonPath("$.referenced_data[0].flow_type").value("LOW"))
        .andExpect(jsonPath("$.referenced_data[0].suggested_action").value("PROMOTION"));
  }

  private MockMvc mockMvc(ChatService chatService) {
    return MockMvcBuilders.standaloneSetup(new ChatController(chatService)).build();
  }
}
