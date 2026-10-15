package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
    @NotBlank(message = "message is required") @Size(max = 4000) String message,
    @JsonProperty("conversation_id") @Size(min = 1, max = 200) String conversationId,
    @JsonProperty("is_resuming_conversation") Boolean resumingConversation) {
  public ChatRequest {
    if (resumingConversation == null) {
      resumingConversation = false;
    }
  }
}
