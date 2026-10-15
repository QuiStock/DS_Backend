package com.quistock.ds_backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quistock.ds_backend.exception.AiIntegrationException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.model.dto.ChatRequest;
import com.quistock.ds_backend.model.dto.ChatResponse;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ChatService {
  private static final Set<String> RESPONSE_STATUSES =
      Set.of("success", "rejected", "clarification_required", "out_of_scope", "error");
  private final RestClient client;
  private final Clock clock;
  private final boolean configured;

  public ChatService(
      @Qualifier("aiRestClient") RestClient client,
      Clock clock,
      @Value("${ai.api.base-url:}") String baseUrl) {
    this.client = client;
    this.clock = clock;
    this.configured = !baseUrl.isBlank();
  }

  public ResponseEntity<ChatResponse> answer(ChatRequest request, String accessToken) {
    String conversationId = conversationId(request);
    if (!configured) {
      throw new AiIntegrationException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "The AI service is not configured.");
    }
    AiMessage message =
        new AiMessage(request.message(), clock.instant(), request.resumingConversation());
    try {
      return client
          .post()
          .uri("/api/v1/conversations/{id}/messages", conversationId)
          .headers(headers -> headers.setBearerAuth(accessToken))
          .contentType(MediaType.APPLICATION_JSON)
          .body(message)
          .exchange(
              (httpRequest, response) -> {
                int status = response.getStatusCode().value();
                if (status != 200 && status != 422) {
                  throw upstreamError(status);
                }
                ChatResponse body = response.bodyTo(ChatResponse.class);
                validateResponse(body, conversationId, status);
                return ResponseEntity.status(status).body(body);
              });
    } catch (ResourceAccessException exception) {
      boolean timeout = isTimeout(exception);
      throw new AiIntegrationException(
          timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE,
          timeout ? "AI_TIMEOUT" : "AI_UNAVAILABLE",
          "The AI service could not process the request.",
          exception);
    } catch (RestClientException exception) {
      throw new AiIntegrationException(
          HttpStatus.BAD_GATEWAY,
          "AI_INVALID_RESPONSE",
          "The AI service returned an invalid response.",
          exception);
    }
  }

  private String conversationId(ChatRequest request) {
    String id = request.conversationId();
    if (id != null && id.isBlank() || request.resumingConversation() && id == null) {
      throw new InvalidRequestException();
    }
    return id == null ? UUID.randomUUID().toString() : id;
  }

  private void validateResponse(ChatResponse body, String conversationId, int status) {
    if (body == null || !conversationId.equals(body.conversationId())) {
      throw upstreamError(502);
    }
    if (body.requestId() == null || body.requestId().isBlank() || body.response() == null) {
      throw upstreamError(502);
    }
    validateResponseStatus(body.status(), status);
  }

  private void validateResponseStatus(String responseStatus, int status) {
    if (responseStatus == null || !RESPONSE_STATUSES.contains(responseStatus)) {
      throw upstreamError(502);
    }
    if (status == 422 && !"rejected".equals(responseStatus)) {
      throw upstreamError(502);
    }
  }

  private AiIntegrationException upstreamError(int status) {
    return switch (status) {
      case 401 ->
          new AiIntegrationException(
              HttpStatus.UNAUTHORIZED,
              "AI_UNAUTHORIZED",
              "The AI service rejected the access token.");
      case 403 ->
          new AiIntegrationException(
              HttpStatus.FORBIDDEN, "AI_FORBIDDEN", "Access to this conversation is forbidden.");
      case 404 ->
          new AiIntegrationException(
              HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "The conversation was not found.");
      case 409 ->
          new AiIntegrationException(
              HttpStatus.CONFLICT,
              "CONVERSATION_STATE_CONFLICT",
              "The conversation cannot accept this operation in its current state.");
      case 429 ->
          new AiIntegrationException(
              HttpStatus.TOO_MANY_REQUESTS,
              "AI_RATE_LIMITED",
              "The AI service rate limit was reached.");
      default ->
          new AiIntegrationException(
              HttpStatus.BAD_GATEWAY,
              "AI_INVALID_RESPONSE",
              "The AI service returned an invalid response.");
    };
  }

  private boolean isTimeout(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  public record AiMessage(
      String message,
      @JsonProperty("sent_at") Instant sentAt,
      @JsonProperty("is_resuming_conversation") boolean resumingConversation) {}
}
