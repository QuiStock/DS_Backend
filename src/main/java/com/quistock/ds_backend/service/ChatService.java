package com.quistock.ds_backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quistock.ds_backend.exception.AiIntegrationException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.model.dto.ChatRequest;
import com.quistock.ds_backend.model.dto.ChatResponse;
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
import tools.jackson.databind.JsonNode;

@Service
public class ChatService {
  private static final Set<String> RESPONSE_STATUSES =
      Set.of("success", "rejected", "clarification_required", "out_of_scope", "error");
  private final RestClient client;
  private final Clock clock;
  private final boolean configured;
  private final ChatConversationProxy conversationProxy;

  public ChatService(
      @Qualifier("aiRestClient") RestClient client,
      Clock clock,
      @Value("${ai.api.base-url:}") String baseUrl) {
    this.client = client;
    this.clock = clock;
    this.configured = !baseUrl.isBlank();
    this.conversationProxy = new ChatConversationProxy(client, baseUrl);
  }

  public ResponseEntity<ChatResponse> answer(ChatRequest request, String accessToken) {
    String conversationId = conversationId(request);
    if (!configured) {
      throw new AiIntegrationException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "The AI service is not configured.");
    }
    AiMessage message =
        new AiMessage(
            request.message(),
            clock.instant(),
            request.resumingConversation(),
            request.productCard());
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
                  throw ChatConversationProxy.upstreamError(status, null);
                }
                ChatResponse body = response.bodyTo(ChatResponse.class);
                validateResponse(body, conversationId, status);
                return ResponseEntity.status(status).body(body);
              });
    } catch (ResourceAccessException exception) {
      boolean timeout = ChatConversationProxy.isTimeout(exception);
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

  public JsonNode endedConversations(String accessToken) {
    return conversationProxy.endedConversations(accessToken);
  }

  public JsonNode history(String conversationId, String accessToken) {
    return conversationProxy.history(conversationId, accessToken);
  }

  public ResponseEntity<JsonNode> end(String conversationId, String accessToken) {
    return conversationProxy.end(conversationId, accessToken);
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
      throw ChatConversationProxy.upstreamError(502, null);
    }
    if (body.requestId() == null || body.requestId().isBlank() || body.response() == null) {
      throw ChatConversationProxy.upstreamError(502, null);
    }
    validateResponseStatus(body.status(), status);
  }

  private void validateResponseStatus(String responseStatus, int status) {
    if (responseStatus == null || !RESPONSE_STATUSES.contains(responseStatus)) {
      throw ChatConversationProxy.upstreamError(502, null);
    }
    if (status == 422 && !"rejected".equals(responseStatus)) {
      throw ChatConversationProxy.upstreamError(502, null);
    }
  }

  public record AiMessage(
      String message,
      @JsonProperty("sent_at") Instant sentAt,
      @JsonProperty("is_resuming_conversation") boolean resumingConversation,
      @JsonProperty("product_card")
          @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          JsonNode productCard) {}
}
