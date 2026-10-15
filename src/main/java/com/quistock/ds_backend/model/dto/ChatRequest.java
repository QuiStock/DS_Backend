package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
    @Schema(
            description = "Mensagem enviada à IA.",
            example = "Quais produtos precisam de reposição?")
        @NotBlank(message = "message is required")
        @Size(max = 4000)
        String message,
    @Schema(description = "String da conversa. Omitir cria UUID; informar reutiliza a conversa.")
        @JsonProperty("conversation_id")
        @Size(min = 1, max = 200)
        String conversationId,
    @Schema(
            description = "true somente ao retomar conversa encerrada; exige conversation_id.",
            defaultValue = "false")
        @JsonProperty("is_resuming_conversation")
        Boolean resumingConversation) {
  public ChatRequest {
    if (resumingConversation == null) {
      resumingConversation = false;
    }
  }
}
