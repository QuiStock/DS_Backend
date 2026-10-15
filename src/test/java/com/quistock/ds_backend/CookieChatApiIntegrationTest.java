package com.quistock.ds_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CookieChatApiIntegrationTest {
  private static final String ISSUER = "https://auth.test.example";
  private static final RSAKey KEY = key();
  private static final HttpServer UPSTREAM = upstream();
  private static final AtomicReference<String> AI_BODY = new AtomicReference<>();
  private static final AtomicInteger AI_STATUS = new AtomicInteger(200);
  private static final AtomicReference<JsonNode> SENT_BODY = new AtomicReference<>();
  private static final AtomicReference<String> SENT_AUTHORIZATION = new AtomicReference<>();
  private static final AtomicReference<String> SENT_COOKIE = new AtomicReference<>();
  private static final AtomicInteger AI_CALLS = new AtomicInteger();
  private static final ObjectMapper JSON = new ObjectMapper();
  private final HttpClient http = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  private int port;

  @Value("${server.servlet.context-path:}")
  private String contextPath;

  private String jwt;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "server.servlet.context-path", () -> System.getProperty("routing.test.context", ""));
    registry.add("auth.jwt.issuer", () -> ISSUER);
    registry.add("auth.jwt.jwk-set-uri", () -> upstreamUrl() + "/jwks");
    registry.add("ai.api.base-url", CookieChatApiIntegrationTest::upstreamUrl);
    registry.add("auth.csrf.cookie-secure", () -> false);
    registry.add("app.cors.allowed-origins", () -> "https://web.test");
  }

  @BeforeEach
  void reset() throws Exception {
    jwt =
        token("101", ISSUER, "quistock-api", "person@example.com", Instant.now().plusSeconds(300));
    AI_STATUS.set(200);
    AI_BODY.set(null);
    AI_CALLS.set(0);
  }

  @AfterAll
  static void close() {
    UPSTREAM.stop(0);
  }

  @Test
  void cookieAuthenticatesAndCsrfAllowsAConversationWithoutLeakingCookiesToAi() throws Exception {
    HttpResponse<String> csrf = send("GET", "/csrf", null, Map.of("Cookie", accessCookie()));
    assertThat(csrf.statusCode()).isEqualTo(200);
    assertThat(csrf.headers().firstValue("cache-control")).hasValue("no-store");
    assertThat(csrf.headers().firstValue("set-cookie").orElseThrow())
        .contains("XSRF-TOKEN=", "Path=/", "HttpOnly", "SameSite=Lax");
    JsonNode csrfBody = JSON.readTree(csrf.body());
    assertThat(csrfBody.path("headerName").asString()).isEqualTo("X-XSRF-TOKEN");

    HttpResponse<String> response =
        send(
            "POST",
            "/chat",
            "{\"message\":\"Olá IA\"}",
            Map.of(
                "Cookie",
                accessCookie() + "; refresh_token=must-not-leak; " + csrfCookie(csrf),
                "X-XSRF-TOKEN",
                csrfBody.path("token").asString()));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("cache-control")).hasValue("no-store");
    JsonNode body = JSON.readTree(response.body());
    assertThat(UUID.fromString(body.path("conversation_id").asString())).isNotNull();
    assertThat(body.path("response").asString()).isEqualTo("Resposta da IA");
    assertThat(body.path("request_id").asString()).isEqualTo("ai-request");
    assertThat(body.has("answer")).isFalse();
    assertThat(SENT_AUTHORIZATION.get()).isEqualTo("Bearer " + jwt);
    assertThat(SENT_COOKIE.get()).isNull();
    assertThat(SENT_BODY.get().path("message").asString()).isEqualTo("Olá IA");
    assertThat(SENT_BODY.get().size()).isEqualTo(3);
    assertThat(SENT_BODY.get().path("is_resuming_conversation").asBoolean()).isFalse();
    Instant sentAt = Instant.parse(SENT_BODY.get().path("sent_at").asString());
    assertThat(sentAt).isBetween(Instant.now().minusSeconds(20), Instant.now().plusSeconds(1));
  }

  @Test
  void bearerStillWorksWithoutCsrfAndPreservesTheConversationString() throws Exception {
    HttpResponse<String> response =
        bearerChat(
            "{\"message\":\"continuar\",\"conversation_id\":\"existing-conversation\",\"is_resuming_conversation\":true}");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(response.body()).path("conversation_id").asString())
        .isEqualTo("existing-conversation");
    assertThat(SENT_BODY.get().path("is_resuming_conversation").asBoolean()).isTrue();
  }

  @Test
  void rejectsCookieWritesWithoutAValidCsrfPairAndBearerCannotBypassConflict() throws Exception {
    for (String method : new String[] {"POST", "PATCH", "PUT", "DELETE"}) {
      assertThat(
              send(method, "/chat", "{\"message\":\"hi\"}", Map.of("Cookie", accessCookie()))
                  .statusCode())
          .isEqualTo(403);
    }
    HttpResponse<String> csrf = send("GET", "/csrf", null, Map.of("Cookie", accessCookie()));
    assertThat(
            send(
                    "POST",
                    "/chat",
                    "{\"message\":\"hi\"}",
                    Map.of(
                        "Cookie",
                        accessCookie() + "; " + csrfCookie(csrf),
                        "X-XSRF-TOKEN",
                        "wrong"))
                .statusCode())
        .isEqualTo(403);
    assertThat(
            send(
                    "POST",
                    "/chat",
                    "{\"message\":\"hi\"}",
                    Map.of(
                        "Cookie",
                        accessCookie(),
                        "X-XSRF-TOKEN",
                        JSON.readTree(csrf.body()).path("token").asString()))
                .statusCode())
        .isEqualTo(403);
    assertThat(
            send(
                    "POST",
                    "/chat",
                    "{\"message\":\"hi\"}",
                    Map.of("Cookie", accessCookie(), "Authorization", "Bearer " + jwt))
                .statusCode())
        .isEqualTo(400);
    assertThat(AI_CALLS.get()).isZero();
  }

  @Test
  void authenticatesOnlyAccessTokensAndRejectsAmbiguousCredentials() throws Exception {
    assertThat(send("GET", "/csrf", null, Map.of()).statusCode()).isEqualTo(401);
    assertThat(send("GET", "/csrf", null, Map.of("Cookie", "refresh_token=opaque")).statusCode())
        .isEqualTo(401);
    assertThat(send("GET", "/csrf", null, Map.of("Cookie", "access_token=broken")).statusCode())
        .isEqualTo(401);
    assertThat(send("GET", "/csrf", null, Map.of("Cookie", "access_token=")).statusCode())
        .isEqualTo(401);
    assertThat(
            send("GET", "/csrf", null, Map.of("Cookie", accessCookie() + "; " + accessCookie()))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            send(
                    "GET",
                    "/csrf",
                    null,
                    Map.of("Cookie", accessCookie(), "Authorization", "Basic abc"))
                .statusCode())
        .isEqualTo(400);
    assertThat(send("POST", "/chat", "{\"message\":\"hi\"}", Map.of()).statusCode()).isEqualTo(401);
  }

  @Test
  void appliesTheExistingJwtClaimValidationToCookies() throws Exception {
    String[] tokens = {
      token("101", ISSUER, "quistock-api", "person@example.com", Instant.now().minusSeconds(120)),
      token(
          "101",
          "https://wrong.test",
          "quistock-api",
          "person@example.com",
          Instant.now().plusSeconds(300)),
      token("101", ISSUER, "wrong-api", "person@example.com", Instant.now().plusSeconds(300)),
      token("0", ISSUER, "quistock-api", "person@example.com", Instant.now().plusSeconds(300)),
      token("abc", ISSUER, "quistock-api", "person@example.com", Instant.now().plusSeconds(300)),
      token(null, ISSUER, "quistock-api", "person@example.com", Instant.now().plusSeconds(300)),
      token("101", ISSUER, "quistock-api", null, Instant.now().plusSeconds(300)),
      token("101", ISSUER, "quistock-api", "person@example.com", null)
    };
    for (String invalid : tokens) {
      assertThat(
              send("GET", "/csrf", null, Map.of("Cookie", "access_token=" + invalid)).statusCode())
          .isEqualTo(401);
    }
  }

  @Test
  void rejectsAnAccessTokenSignedByAnUntrustedKey() throws Exception {
    SignedJWT original = SignedJWT.parse(jwt);
    SignedJWT untrusted = new SignedJWT(original.getHeader(), original.getJWTClaimsSet());
    untrusted.sign(new RSASSASigner(key()));
    assertThat(
            send("GET", "/csrf", null, Map.of("Cookie", "access_token=" + untrusted.serialize()))
                .statusCode())
        .isEqualTo(401);
    assertThat(AI_CALLS.get()).isZero();
  }

  @Test
  void allowsOnlyConfiguredOriginsAndCredentialedPreflight() throws Exception {
    HttpResponse<String> allowed =
        send(
            "OPTIONS",
            "/chat",
            null,
            Map.of(
                "Origin",
                "https://web.test",
                "Access-Control-Request-Method",
                "POST",
                "Access-Control-Request-Headers",
                "content-type,x-csrf-token"));
    assertThat(allowed.statusCode()).isEqualTo(200);
    assertThat(allowed.headers().firstValue("access-control-allow-origin"))
        .hasValue("https://web.test");
    assertThat(allowed.headers().firstValue("access-control-allow-credentials")).hasValue("true");
    assertThat(
            send(
                    "GET",
                    "/csrf",
                    null,
                    Map.of("Origin", "https://evil.test", "Cookie", accessCookie()))
                .statusCode())
        .isEqualTo(403);
    assertThat(
            send(
                    "GET",
                    "/csrf",
                    null,
                    Map.of("Origin", "https://web.test", "Cookie", accessCookie()))
                .statusCode())
        .isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"message\":\"\"}",
        "{\"message\":\"   \"}",
        "{\"message\":\"hi\",\"is_resuming_conversation\":true}",
        "{\"message\":\"hi\",\"conversation_id\":\" \"}"
      })
  void validatesThePublicChatRequest(String body) throws Exception {
    assertThat(bearerChat(body).statusCode()).isEqualTo(400);
    assertThat(AI_CALLS.get()).isZero();
  }

  @Test
  void rejectsMessagesAboveTheAiLimit() throws Exception {
    assertThat(
            bearerChat(JSON.writeValueAsString(Map.of("message", "a".repeat(4001)))).statusCode())
        .isEqualTo(400);
    assertThat(AI_CALLS.get()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"success", "clarification_required", "out_of_scope", "error", "rejected"})
  void preservesAiApplicationStatuses(String status) throws Exception {
    AI_BODY.set(responseBody("conversation", status));
    HttpResponse<String> response =
        bearerChat("{\"message\":\"hi\",\"conversation_id\":\"conversation\"}");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(response.body()).path("status").asString()).isEqualTo(status);
  }

  @Test
  void preservesAnAiGuardrailRejection() throws Exception {
    AI_STATUS.set(422);
    AI_BODY.set(responseBody("conversation", "rejected"));
    HttpResponse<String> response =
        bearerChat("{\"message\":\"hi\",\"conversation_id\":\"conversation\"}");
    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(JSON.readTree(response.body()).path("status").asString()).isEqualTo("rejected");
  }

  @ParameterizedTest
  @CsvSource({
    "401,401,AI_UNAUTHORIZED",
    "403,403,AI_FORBIDDEN",
    "404,404,CONVERSATION_NOT_FOUND",
    "409,409,CONVERSATION_STATE_CONFLICT",
    "429,429,AI_RATE_LIMITED",
    "500,502,AI_INVALID_RESPONSE",
    "503,502,AI_INVALID_RESPONSE"
  })
  void mapsUpstreamErrorsWithoutExposingUpstreamDetails(
      int upstreamStatus, int expected, String code) throws Exception {
    AI_STATUS.set(upstreamStatus);
    AI_BODY.set("{\"secret\":\"do-not-expose\"}");
    HttpResponse<String> response = bearerChat("{\"message\":\"hi\"}");
    assertThat(response.statusCode()).isEqualTo(expected);
    assertThat(JSON.readTree(response.body()).path("error").asString()).isEqualTo(code);
    assertThat(response.body()).doesNotContain("do-not-expose");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "not-json",
        "{}",
        "{\"conversation_id\":\"other\",\"request_id\":\"r\",\"response\":\"x\",\"status\":\"success\"}",
        "{\"conversation_id\":\"conversation\",\"request_id\":\"r\",\"response\":\"x\",\"status\":\"unknown\"}"
      })
  void rejectsMalformedOrMismatchedAiResponses(String body) throws Exception {
    AI_BODY.set(body);
    HttpResponse<String> response =
        bearerChat("{\"message\":\"hi\",\"conversation_id\":\"conversation\"}");
    assertThat(response.statusCode()).isEqualTo(502);
  }

  private HttpResponse<String> bearerChat(String body) throws Exception {
    return send("POST", "/chat", body, Map.of("Authorization", "Bearer " + jwt));
  }

  private HttpResponse<String> send(
      String method, String path, String body, Map<String, String> headers) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + contextPath + path));
    headers.forEach(builder::header);
    if (body != null) {
      builder.header("Content-Type", "application/json");
    }
    return http.send(
        builder
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private String accessCookie() {
    return "access_token=" + jwt;
  }

  private String csrfCookie(HttpResponse<String> response) {
    return response.headers().allValues("set-cookie").stream()
        .filter(value -> value.startsWith("XSRF-TOKEN="))
        .findFirst()
        .orElseThrow()
        .split(";")[0];
  }

  private static String upstreamUrl() {
    return "http://127.0.0.1:" + UPSTREAM.getAddress().getPort();
  }

  private static String responseBody(String id, String status) {
    return "{\"conversation_id\":\""
        + id
        + "\",\"request_id\":\"ai-request\",\"response\":\"Resposta da IA\",\"status\":\""
        + status
        + "\"}";
  }

  private static HttpServer upstream() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/jwks",
          exchange -> {
            byte[] bytes =
                new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
              output.write(bytes);
            }
          });
      server.createContext(
          "/api/v1/conversations/",
          exchange -> {
            AI_CALLS.incrementAndGet();
            SENT_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
            SENT_COOKIE.set(exchange.getRequestHeaders().getFirst("Cookie"));
            SENT_BODY.set(JSON.readTree(exchange.getRequestBody()));
            String id = exchange.getRequestURI().getPath().split("/")[4];
            String body = AI_BODY.get() == null ? responseBody(id, "success") : AI_BODY.get();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(AI_STATUS.get(), bytes.length == 0 ? -1 : bytes.length);
            try (var output = exchange.getResponseBody()) {
              output.write(bytes);
            }
          });
      server.start();
      return server;
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static RSAKey key() {
    try {
      return new RSAKeyGenerator(2048).keyID("integration-test").generate();
    } catch (com.nimbusds.jose.JOSEException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String token(String subject, String issuer, String audience, String email, Instant expiry)
      throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .issuer(issuer)
            .audience(audience)
            .claim("email", email)
            .issueTime(new Date())
            .expirationTime(expiry == null ? null : Date.from(expiry))
            .build();
    SignedJWT token =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
    token.sign(new RSASSASigner(KEY));
    return token.serialize();
  }
}
