package com.quistock.ds_backend.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quistock.ds_backend.handler.ApiExceptionHandler;
import com.quistock.ds_backend.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatControllerTest {
  @Test
  void rejectsMissingMessageBeforeCallingAi() throws Exception {
    MockMvcBuilders.standaloneSetup(new ChatController(mock(ChatService.class)))
        .setControllerAdvice(new ApiExceptionHandler())
        .build()
        .perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
  }
}
