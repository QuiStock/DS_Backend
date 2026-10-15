package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatResponse(
    @JsonProperty("conversation_id") String conversationId,
    @JsonProperty("request_id") String requestId,
    String response,
    String status) {}
