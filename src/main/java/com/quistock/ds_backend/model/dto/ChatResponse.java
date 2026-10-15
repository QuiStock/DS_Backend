package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatResponse(
    @JsonProperty("conversation_id") String conversationId,
    @JsonProperty("request_id") String requestId,
    String response,
    @Schema(
            allowableValues = {
              "success",
              "rejected",
              "clarification_required",
              "out_of_scope",
              "error"
            })
        String status) {}
