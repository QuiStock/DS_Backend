package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quistock.ds_backend.config.AiClientConfig;
import com.quistock.ds_backend.exception.AiIntegrationException;
import com.quistock.ds_backend.model.dto.ChatRequest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ChatServiceTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void forwardsConversationHistoryWithBearerToken() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://ai.test/api/v1/conversations/abc/messages"))
        .andExpect(header("Authorization", "Bearer token"))
        .andRespond(
            withSuccess(
                "{\"conversation_id\":\"abc\",\"messages\":[]}", MediaType.APPLICATION_JSON));
    ChatService service = new ChatService(builder.build(), CLOCK, "http://ai.test");
    assertThat(service.history("abc", "token").get("conversation_id").asText()).isEqualTo("abc");
    server.verify();
  }

  @Test
  void proxiesEndedConversationsAndEndResponseWithoutChangingJson() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://ai.test/api/v1/conversations/ended"))
        .andExpect(header("Authorization", "Bearer token"))
        .andRespond(
            withSuccess(
                "{\"conversations\":[{\"conversation_id\":\"abc\",\"title\":null,"
                    + "\"updated_at\":\"2026-10-12T12:00:00Z\"}]}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("http://ai.test/api/v1/conversations/abc/end"))
        .andExpect(header("Authorization", "Bearer token"))
        .andRespond(
            withStatus(HttpStatus.ACCEPTED)
                .body(
                    "{\"conversation_id\":\"abc\",\"request_id\":\"r\","
                        + "\"job_id\":\"j\",\"status\":\"ended\","
                        + "\"summary_status\":\"queued\"}")
                .contentType(MediaType.APPLICATION_JSON));
    ChatService service = new ChatService(builder.build(), CLOCK, "http://ai.test");

    assertThat(service.endedConversations("token").path("conversations").size()).isEqualTo(1);
    assertThat(service.end("abc", "token").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    server.verify();
  }

  @Test
  void encodesConversationIdAsOnePathSegment() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://ai.test/api/v1/conversations/abc%2F..%2Fdef/messages"))
        .andRespond(
            withSuccess(
                "{\"conversation_id\":\"abc/../def\",\"messages\":[]}",
                MediaType.APPLICATION_JSON));
    ChatService service = new ChatService(builder.build(), CLOCK, "http://ai.test");

    assertThat(service.history("abc/../def", "token").path("messages").isEmpty()).isTrue();
    server.verify();
  }

  @Test
  void mapsUpstreamConversationErrorsToTheCoreContract() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://ai.test/api/v1/conversations/missing/messages"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    ChatService service = new ChatService(builder.build(), CLOCK, "http://ai.test");

    assertThatThrownBy(() -> service.history("missing", "token"))
        .isInstanceOfSatisfying(
            AiIntegrationException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(error.code()).isEqualTo("CONVERSATION_NOT_FOUND");
            });
    server.verify();
  }

  @Test
  void sendsServerTimeInsteadOfClientIdentityOrTimestamp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://ai.test/api/v1/conversations/existing/messages"))
        .andExpect(header("Authorization", "Bearer original-token"))
        .andExpect(
            content()
                .json(
                    """
                    {"message":"hi","sent_at":"2026-10-12T12:00:00Z","is_resuming_conversation":false}
                    """))
        .andRespond(
            withSuccess(
                """
                {"conversation_id":"existing","request_id":"request","response":"ok","status":"success"}
                """,
                MediaType.APPLICATION_JSON));
    ChatService service = new ChatService(builder.build(), CLOCK, "http://ai.test");
    assertThat(
            service
                .answer(new ChatRequest("hi", "existing", false), "original-token")
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    server.verify();
  }

  @Test
  void forwardsOptionalProductCardWithoutInterpretingItsIdentifiers() throws Exception {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://ai.test/api/v1/conversations/id/messages"))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "message":"explain",
                      "sent_at":"2026-10-12T12:00:00Z",
                      "is_resuming_conversation":false,
                      "product_card":{"product_id":"sku:abc","product_name":"Arroz","batch_count":2}
                    }
                    """))
        .andRespond(
            withSuccess(
                "{\"conversation_id\":\"id\",\"request_id\":\"r\","
                    + "\"response\":\"ok\",\"status\":\"success\"}",
                MediaType.APPLICATION_JSON));
    ChatService service = new ChatService(builder.build(), CLOCK, "http://ai.test");

    service.answer(
        new ChatRequest(
            "explain",
            "id",
            false,
            new tools.jackson.databind.ObjectMapper()
                .readTree(
                    "{\"product_id\":\"sku:abc\",\"product_name\":\"Arroz\",\"batch_count\":2}")),
        "token");
    server.verify();
  }

  @Test
  void reportsMissingConfigurationWithoutAnHttpCall() {
    ChatService service = new ChatService(RestClient.create(), CLOCK, "");
    assertThatThrownBy(() -> service.answer(new ChatRequest("hi", null, false), "token"))
        .isInstanceOfSatisfying(
            AiIntegrationException.class,
            error -> assertThat(error.code()).isEqualTo("AI_NOT_CONFIGURED"));
  }

  @Test
  void mapsConnectionFailureToServiceUnavailable() throws Exception {
    HttpServer stopped = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int unusedPort = stopped.getAddress().getPort();
    stopped.start();
    stopped.stop(0);
    ChatService service =
        new ChatService(
            RestClient.create("http://127.0.0.1:" + unusedPort), CLOCK, "http://configured");
    assertThatThrownBy(() -> service.answer(new ChatRequest("hi", null, false), "token"))
        .isInstanceOfSatisfying(
            AiIntegrationException.class,
            error -> assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
  }

  @Test
  void mapsReadTimeoutToGatewayTimeout() throws Exception {
    HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AiClientConfig config = new AiClientConfig();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor();
        var httpClient = config.aiHttpClient(5000)) {
      slow.setExecutor(executor);
      slow.createContext(
          "/",
          exchange -> {
            try {
              Thread.sleep(1000);
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            }
            exchange.close();
          });
      slow.start();
      String url = "http://127.0.0.1:" + slow.getAddress().getPort();
      // Same transport and timeout configuration used by the production client.
      RestClient client = config.aiRestClient(httpClient, url, 50);
      ChatService service = new ChatService(client, CLOCK, url);
      assertThatThrownBy(() -> service.answer(new ChatRequest("hi", null, false), "token"))
          .isInstanceOfSatisfying(
              AiIntegrationException.class,
              error -> assertThat(error.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    } finally {
      slow.stop(0);
    }
  }
}
