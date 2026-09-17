package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.ChatRequest;
import com.quistock.ds_backend.model.dto.ChatResponse;
import com.quistock.ds_backend.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController {
  private final ChatService chatService;

  public ChatController(ChatService service) {
    this.chatService = service;
  }

  @PostMapping
  public ChatResponse answer(@Valid @RequestBody ChatRequest request) {
    return chatService.answer(request);
  }
}
