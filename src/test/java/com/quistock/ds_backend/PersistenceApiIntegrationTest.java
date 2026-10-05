package com.quistock.ds_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PersistenceApiIntegrationTest {
  private static final long TEST_USER_ID = 1001L;
  private static final String TEST_ISSUER = "https://auth.test.example";
  private static final String TEST_AUDIENCE = "quistock-api";
  private static final RSAKey TEST_SIGNING_KEY = newTestSigningKey();

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("quistock_test")
          .withUsername("quistock")
          .withPassword("quistock");

  private static final AtomicReference<String> ERP_RESPONSE = new AtomicReference<>("[]");
  private static HttpServer erpServer;

  @Value("${local.server.port}")
  private int port;

  private final HttpClient http = HttpClient.newHttpClient();
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private com.quistock.ds_backend.service.ErpSyncService erpSyncService;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    startErpServer();
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> true);
    registry.add(
        "server.servlet.context-path", () -> System.getProperty("routing.test.context", ""));
    registry.add("auth.jwt.issuer", () -> TEST_ISSUER);
    registry.add("auth.jwt.audience", () -> TEST_AUDIENCE);
    registry.add(
        "auth.jwt.jwk-set-uri",
        () -> "http://127.0.0.1:" + erpServer.getAddress().getPort() + "/jwks");
    registry.add("erp.sync.enabled", () -> true);
    registry.add("erp.sync.initial-delay-ms", () -> 3_600_000);
    registry.add("erp.api.base-url", () -> "http://127.0.0.1:" + erpServer.getAddress().getPort());
  }

  @AfterAll
  static void stopErpServer() {
    if (erpServer != null) {
      erpServer.stop(0);
    }
  }

  @Test
  void persistsErpDataAndServesTheOperationalApiFlow() throws Exception {
    assertRequestWithoutTokenIsUnauthorized();
    assertRequestWithInvalidTokenIsUnauthorized();
    assertRequestWithInvalidClaimsIsUnauthorized();
    seedAuthenticatedUser();
    ERP_RESPONSE.set(initialErpPayload());

    JsonNode products = get("/products", 200);
    assertThat(products.size()).isEqualTo(3);
    assertThat(product("SKU-HIGH:STORE-1").path("current_stock").asInt()).isEqualTo(8);
    assertThat(get("/branches", 200).size()).isEqualTo(2);
    assertThat(get("/products/SKU-HIGH:STORE-1", 200).path("id").asText())
        .isEqualTo("SKU-HIGH:STORE-1");
    assertThat(get("/erp-integration/status", 200).path("status").asText()).isEqualTo("CONNECTED");

    String validSnapshot = initialErpPayload();
    shouldRejectSnapshot(
        validSnapshot.replaceFirst("\"data_validade\":\"[^\"]+\"", "\"data_validade\":null"));
    shouldRejectSnapshot(
        validSnapshot.replaceFirst(
            "\"data_validade\":\"[^\"]+\"", "\"data_validade\":\"invalid-date\""));
    shouldRejectSnapshot(validSnapshot.replaceFirst("\"preco\":\"[^\"]+\"", "\"preco\":null"));
    shouldRejectSnapshot(validSnapshot.replaceFirst("\"preco\":\"[^\"]+\"", "\"preco\":\"-1.00\""));
    shouldRejectSnapshot(validSnapshot.replaceFirst("\"custo\":\"[^\"]+\"", "\"custo\":null"));
    shouldRejectSnapshot(validSnapshot.replaceFirst("\"custo\":\"[^\"]+\"", "\"custo\":\"-1.00\""));
    shouldRejectSnapshot(validSnapshot.replaceFirst("SKU-HIGH", "A".repeat(101)));

    String highFlowId = analyze("SKU-HIGH:STORE-1", "HIGH");
    String mediumFlowId = analyze("SKU-MEDIUM:STORE-1", "MEDIUM");
    String lowFlowId = analyze("SKU-LOW:South Branch", "LOW");
    assertThat(
            post("/chat", "{\"message\":\"Which products need a promotion?\"}", 200)
                .path("referenced_data")
                .isArray())
        .isTrue();
    assertThat(get("/flows?flow_type=HIGH", 200).size()).isEqualTo(1);
    assertThat(get("/flows?product_id=SKU-HIGH:STORE-1&status=ANALYZED", 200).size()).isEqualTo(1);

    JsonNode order = post("/actions/generate", "{\"flow_id\":\"" + highFlowId + "\"}", 201);
    JsonNode promotion =
        post(
            "/actions/generate",
            """
            {"flow_id":"%s","promotion_valid_from":"%s","promotion_valid_until":"%s"}
            """
                .formatted(lowFlowId, LocalDate.now().plusDays(1), LocalDate.now().plusDays(8)),
            201);
    JsonNode noAction = post("/actions/generate", "{\"flow_id\":\"" + mediumFlowId + "\"}", 201);
    assertThat(order.path("generated_actions").size()).isEqualTo(1);
    assertThat(promotion.path("generated_actions").size()).isEqualTo(1);
    assertThat(noAction.path("generated_actions").size()).isZero();

    long promotionSuggestionId =
        Long.parseLong(promotion.path("generated_actions").get(0).path("id").asText());
    assertThat(
            jdbc.queryForObject(
                "SELECT created_by_id FROM suggestion WHERE id = ?",
                Long.class,
                promotionSuggestionId))
        .isEqualTo(TEST_USER_ID);

    String orderId = order.path("generated_actions").get(0).path("id").asText();
    String promotionId = promotion.path("generated_actions").get(0).path("id").asText();
    patch("/actions/" + promotionId + "/status", "{\"status\":\"IN_EMPLOYEE_TRIAGE\"}", 200);
    assertThat(
            jdbc.queryForObject(
                "SELECT employee_id FROM suggestion_triage WHERE suggestion_id = ?",
                Long.class,
                promotionSuggestionId))
        .isEqualTo(TEST_USER_ID);
    patch("/actions/" + promotionId + "/status", "{\"status\":\"SENT_TO_MANAGER\"}", 200);
    patch(
        "/actions/" + promotionId + "/status",
        """
        {"status":"APPROVED","final_promotion_valid_from":"%s","final_promotion_valid_until":"%s"}
        """
            .formatted(LocalDate.now().plusDays(2), LocalDate.now().plusDays(9)),
        200);
    assertThat(
            jdbc.queryForObject(
                "SELECT manager_id FROM suggestion_decision WHERE suggestion_id = ?",
                Long.class,
                promotionSuggestionId))
        .isEqualTo(TEST_USER_ID);
    patch(
        "/actions/" + orderId + "/status",
        "{\"status\":\"REJECTED\",\"justification\":\"Demand changed\"}",
        200);

    assertThat(get("/actions?status=APPROVED&action_type=PROMOTION", 200).size()).isEqualTo(1);
    assertThat(get("/dashboard/summary", 200).isObject()).isTrue();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM suggestion_log", Integer.class))
        .isEqualTo(6);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM suggestion_log WHERE user_id = ?",
                Integer.class,
                TEST_USER_ID))
        .isEqualTo(6);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM suggestion_decision", Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM suggestion_triage", Integer.class))
        .isEqualTo(1);

    ERP_RESPONSE.set(updatedErpPayload());
    assertThat(erpSyncService.syncNow()).isTrue();
    assertThat(get("/products", 200).size()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM batch WHERE active = FALSE", Integer.class))
        .isEqualTo(3);

    ERP_RESPONSE.set("[]");
    assertThatThrownBy(erpSyncService::syncNow).isInstanceOf(IllegalStateException.class);
    assertThat(erpSyncService.hasCompletedSync()).isTrue();
    assertThat(get("/products", 200).size()).isEqualTo(1);
  }

  private String analyze(String productId, String expectedType) throws Exception {
    JsonNode flow = post("/flows/analyze", "{\"product_id\":\"" + productId + "\"}", 201);
    assertThat(flow.path("flow_type").asText()).isEqualTo(expectedType);
    return flow.path("id").asText();
  }

  private JsonNode product(String publicId) throws Exception {
    return get("/products/" + publicId, 200);
  }

  private void shouldRejectSnapshot(String payload) throws Exception {
    ERP_RESPONSE.set(payload);
    assertThatThrownBy(erpSyncService::syncNow)
        .isInstanceOf(com.quistock.ds_backend.exception.ErpIntegrationException.class);
    assertThat(get("/products", 200).size()).isEqualTo(3);
  }

  private void seedAuthenticatedUser() {
    jdbc.update("INSERT INTO role (id, code, name) VALUES (?, 'TEST', 'Test')", TEST_USER_ID);
    jdbc.update(
        "INSERT INTO user_account (id, role_id, name, email, password_hash) VALUES (?, ?, ?, ?, ?)",
        TEST_USER_ID,
        TEST_USER_ID,
        "Mobile User",
        "mobile@test.example",
        "test-hash");
  }

  private void assertRequestWithoutTokenIsUnauthorized() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:"
                        + port
                        + System.getProperty("routing.test.context", "")
                        + "/products"))
            .GET()
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(401);
  }

  private void assertRequestWithInvalidTokenIsUnauthorized() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:"
                        + port
                        + System.getProperty("routing.test.context", "")
                        + "/products"))
            .header("Authorization", "Bearer invalid-token")
            .GET()
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(401);
  }

  private void assertRequestWithInvalidClaimsIsUnauthorized() throws Exception {
    assertUnauthorized(withInvalidSignature(validToken()));
    assertUnauthorized(
        signedToken(
            TEST_ISSUER,
            "another-api",
            "1001",
            "mobile@test.example",
            Instant.now().plusSeconds(300)));
    assertUnauthorized(
        signedToken(
            TEST_ISSUER, null, "1001", "mobile@test.example", Instant.now().plusSeconds(300)));
    assertUnauthorized(
        signedToken(
            TEST_ISSUER,
            TEST_AUDIENCE,
            "not-a-sql-id",
            "mobile@test.example",
            Instant.now().plusSeconds(300)));
    assertUnauthorized(
        signedToken(
            "https://wrong-issuer.test.example",
            TEST_AUDIENCE,
            "1001",
            "mobile@test.example",
            Instant.now().plusSeconds(300)));
    assertUnauthorized(
        signedToken(TEST_ISSUER, TEST_AUDIENCE, "1001", null, Instant.now().plusSeconds(300)));
    assertUnauthorized(
        signedToken(
            TEST_ISSUER,
            TEST_AUDIENCE,
            "1001",
            "mobile@test.example",
            Instant.now().minusSeconds(600)));
  }

  private void assertUnauthorized(String token) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:"
                        + port
                        + System.getProperty("routing.test.context", "")
                        + "/products"))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(401);
  }

  private JsonNode get(String path, int expectedStatus) throws Exception {
    return exchange(HttpMethod.GET, path, null, expectedStatus);
  }

  private JsonNode post(String path, String body, int expectedStatus) throws Exception {
    return exchange(HttpMethod.POST, path, body, expectedStatus);
  }

  private JsonNode patch(String path, String body, int expectedStatus) throws Exception {
    return exchange(HttpMethod.PATCH, path, body, expectedStatus);
  }

  private JsonNode exchange(HttpMethod method, String path, String body, int expectedStatus)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:"
                        + port
                        + System.getProperty("routing.test.context", "")
                        + path))
            .header("Authorization", "Bearer " + validToken());
    if (body == null) {
      request.GET();
    } else {
      request
          .header("Content-Type", "application/json")
          .method(method.name(), HttpRequest.BodyPublishers.ofString(body));
    }
    HttpResponse<String> response =
        http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode())
        .as("%s %s returned %s", method, path, response.body())
        .isEqualTo(expectedStatus);
    return objectMapper.readTree(response.body());
  }

  private static void startErpServer() {
    if (erpServer != null) {
      return;
    }
    try {
      erpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      erpServer.createContext(
          "/jwks",
          exchange -> {
            byte[] body =
                new JWKSet(TEST_SIGNING_KEY.toPublicJWK())
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
              output.write(body);
            }
          });
      erpServer.createContext(
          "/products",
          exchange -> {
            byte[] body = ERP_RESPONSE.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
              output.write(body);
            }
          });
      erpServer.start();
    } catch (IOException exception) {
      throw new IllegalStateException("Could not start the test ERP endpoint.", exception);
    }
  }

  private static String initialErpPayload() {
    LocalDate today = LocalDate.now();
    return """
    [
      {
        "id":"HIGH-1","codigo_produto_erp":"SKU-HIGH","nome_produto":"Milk",
        "categoria":"Dairy","num_lote":"H-1","data_validade":"%s",
        "quantidade":"3","preco":"7.90","custo":"5.20",
        "codigo_filial_erp":"STORE-1","filial":"North Branch",
        "certificado_qualidade":true,"data_entrada":"2026-08-20T08:00:00Z",
        "vendas_7d":"7","vendas_30d":"30","estoque_minimo":"10","lead_time_dias":"5"
      },
      {
        "id":"HIGH-2","codigo_produto_erp":"SKU-HIGH","nome_produto":"Milk",
        "categoria":"Dairy","num_lote":"H-2","data_validade":"%s",
        "quantidade":"5","preco":"8.10","custo":"5.40",
        "codigo_filial_erp":"STORE-1","filial":"North Branch",
        "certificado_qualidade":true,"data_entrada":"2026-08-21T08:00:00Z",
        "vendas_7d":"7","vendas_30d":"30","estoque_minimo":"10","lead_time_dias":"5"
      },
      {
        "id":"MED-1","codigo_produto_erp":"SKU-MEDIUM","nome_produto":"Oats",
        "categoria":"Cereals","num_lote":"M-1","data_validade":"%s",
        "quantidade":"50","preco":"4.50","custo":"2.70",
        "codigo_filial_erp":"STORE-1","filial":"North Branch",
        "certificado_qualidade":true,"data_entrada":"2026-08-22T08:00:00Z",
        "vendas_7d":"7","vendas_30d":"30","estoque_minimo":"20","lead_time_dias":"5"
      },
      {
        "id":"LOW-1","codigo_produto_erp":"SKU-LOW","nome_produto":"Juice",
        "categoria":"Beverages","num_lote":"L-1","data_validade":"%s",
        "quantidade":"100","preco":"3.50","custo":"1.90",
        "filial":"South Branch","certificado_qualidade":false,
        "data_entrada":"2026-08-23T08:00:00Z","vendas_7d":"0","vendas_30d":"0",
        "estoque_minimo":"20","lead_time_dias":"3"
      }
    ]
    """
        .formatted(today.plusDays(30), today.plusDays(45), today.plusDays(365), today.plusDays(5));
  }

  private static String updatedErpPayload() {
    return """
    [{
      "id":"HIGH-1","codigo_produto_erp":"SKU-HIGH","nome_produto":"Milk",
      "categoria":"Dairy","num_lote":"H-1","data_validade":"%s",
      "quantidade":"10","preco":"8.25","custo":"5.50",
      "codigo_filial_erp":"STORE-1","filial":"North Branch",
      "certificado_qualidade":true,"data_entrada":"2026-08-24T08:00:00Z",
      "vendas_7d":"7","vendas_30d":"30","estoque_minimo":"10","lead_time_dias":"5"
    }]
    """
        .formatted(LocalDate.now().plusDays(30));
  }

  private static RSAKey newTestSigningKey() {
    try {
      return new RSAKeyGenerator(2048).keyID("test-key").generate();
    } catch (JOSEException exception) {
      throw new IllegalStateException("Could not create a test signing key.", exception);
    }
  }

  private static String validToken() {
    return signedToken(
        TEST_ISSUER,
        TEST_AUDIENCE,
        Long.toString(TEST_USER_ID),
        "mobile@test.example",
        Instant.now().plusSeconds(300));
  }

  private static String withInvalidSignature(String token) {
    int signatureStart = token.lastIndexOf('.') + 1;
    char firstSignatureCharacter = token.charAt(signatureStart);
    char replacement = firstSignatureCharacter == 'A' ? 'B' : 'A';
    return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
  }

  private static String signedToken(
      String issuer, String audience, String subject, String email, Instant expiresAt) {
    JWTClaimsSet.Builder claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(expiresAt))
            .jwtID(java.util.UUID.randomUUID().toString());
    if (audience != null) {
      claims.audience(audience);
    }
    if (subject != null) {
      claims.subject(subject);
    }
    if (email != null) {
      claims.claim("email", email);
    }
    SignedJWT token =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TEST_SIGNING_KEY.getKeyID()).build(),
            claims.build());
    try {
      token.sign(new RSASSASigner(TEST_SIGNING_KEY));
      return token.serialize();
    } catch (JOSEException exception) {
      throw new IllegalStateException("Could not sign a test access token.", exception);
    }
  }
}
