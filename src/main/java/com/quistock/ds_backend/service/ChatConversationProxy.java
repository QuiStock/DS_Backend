package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.AiIntegrationException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

final class ChatConversationProxy {
  private final RestClient client;
  private final boolean configured;

  ChatConversationProxy(RestClient client, String baseUrl) {
    this.client = client;
    this.configured = !baseUrl.isBlank();
  }

  JsonNode endedConversations(String accessToken) {
    return proxy(HttpMethod.GET, "/api/v1/conversations/ended", accessToken, 200).getBody();
  }

  JsonNode history(String conversationId, String accessToken) {
    validateConversationId(conversationId);
    return proxy(
            HttpMethod.GET, "/api/v1/conversations/{id}/messages", accessToken, 200, conversationId)
        .getBody();
  }

  ResponseEntity<JsonNode> end(String conversationId, String accessToken) {
    validateConversationId(conversationId);
    return proxy(
        HttpMethod.POST, "/api/v1/conversations/{id}/end", accessToken, 202, conversationId);
  }

  private ResponseEntity<JsonNode> proxy(
      HttpMethod method,
      String path,
      String accessToken,
      int successStatus,
      Object... uriVariables) {
    if (!configured) {
      throw new AiIntegrationException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "The AI service is not configured.");
    }
    try {
      return client
          .method(method)
          .uri(path, uriVariables)
          .headers(headers -> headers.setBearerAuth(accessToken))
          .exchange((request, response) -> response(response, successStatus));
    } catch (RestClientException exception) {
      throw integrationError(exception);
    }
  }

  // The HTTP boundary must read this status to preserve AI's 202 response.
  @SuppressWarnings("PMD.LawOfDemeter")
  private ResponseEntity<JsonNode> response(
      RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response, int successStatus)
      throws IOException {
    int status = response.getStatusCode().value();
    if (status != successStatus) {
      throw upstreamError(status, null);
    }
    JsonNode body = response.bodyTo(JsonNode.class);
    if (body == null) {
      throw upstreamError(502, null);
    }
    return ResponseEntity.status(status).body(body);
  }

  private AiIntegrationException integrationError(RestClientException exception) {
    if (exception instanceof RestClientResponseException responseException) {
      HttpStatusCode responseStatus = responseException.getStatusCode();
      return upstreamError(responseStatus.value(), responseException);
    }
    if (exception instanceof ResourceAccessException accessException) {
      boolean timeout = isTimeout(accessException);
      return new AiIntegrationException(
          timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE,
          timeout ? "AI_TIMEOUT" : "AI_UNAVAILABLE",
          "The AI service could not process the request.",
          accessException);
    }
    return new AiIntegrationException(
        HttpStatus.BAD_GATEWAY,
        "AI_INVALID_RESPONSE",
        "The AI service returned an invalid response.",
        exception);
  }

  private void validateConversationId(String id) {
    if (id == null || id.isBlank() || id.length() > 200) {
      throw new InvalidRequestException();
    }
  }

  static AiIntegrationException upstreamError(int status, Throwable cause) {
    AiIntegrationException error =
        switch (status) {
          case 401 ->
              new AiIntegrationException(
                  HttpStatus.UNAUTHORIZED,
                  "AI_UNAUTHORIZED",
                  "The AI service rejected the access token.");
          case 403 ->
              new AiIntegrationException(
                  HttpStatus.FORBIDDEN,
                  "AI_FORBIDDEN",
                  "Access to this conversation is forbidden.");
          case 404 ->
              new AiIntegrationException(
                  HttpStatus.NOT_FOUND,
                  "CONVERSATION_NOT_FOUND",
                  "The conversation was not found.");
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
    return cause == null
        ? error
        : new AiIntegrationException(error.status(), error.code(), error.getMessage(), cause);
  }

  static boolean isTimeout(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
