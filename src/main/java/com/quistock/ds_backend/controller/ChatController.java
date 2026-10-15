package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.ChatRequest;
import com.quistock.ds_backend.model.dto.ChatResponse;
import com.quistock.ds_backend.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
  public ResponseEntity<ChatResponse> answer(
      @Valid @RequestBody ChatRequest request, @AuthenticationPrincipal Jwt jwt) {
    ResponseEntity<ChatResponse> result = chatService.answer(request, jwt.getTokenValue());
    return ResponseEntity.status(result.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(result.getBody());
  }
}
