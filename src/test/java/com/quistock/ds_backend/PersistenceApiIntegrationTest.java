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
import org.testcontainers.utility.DockerImageName;
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
      new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
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
  @Autowired private com.quistock.ds_backend.service.DailyAnalysisService dailyAnalysis;

  @Autowired
  private com.quistock.ds_backend.repository.AnalysisExecutionRepository analysisExecutions;

  @Autowired private com.quistock.ds_backend.repository.ProductRepository analysisProducts;
  @Autowired private com.quistock.ds_backend.service.FlowService analysisFlows;
  @Autowired private com.quistock.ds_backend.repository.ErpProjectionLock projectionLock;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    startErpServer();
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> 6);
    registry.add(
        "server.servlet.context-path", () -> System.getProperty("routing.test.context", ""));
    registry.add("auth.jwt.issuer", () -> TEST_ISSUER);
    registry.add("auth.jwt.audience", () -> TEST_AUDIENCE);
    registry.add(
        "app.cors.allowed-origins", () -> "https://managers.example,https://other.example");
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
  void allowsCredentialedCorsFromConfiguredOrigins() throws Exception {
    for (String origin : new String[] {"https://managers.example", "https://other.example"}) {
      HttpRequest preflight =
          HttpRequest.newBuilder(URI.create(baseUrl() + "/team-members"))
              .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
              .header("Origin", origin)
              .header("Access-Control-Request-Method", "PATCH")
              .header("Access-Control-Request-Headers", "x-xsrf-token,content-type")
              .build();
      HttpResponse<Void> preflightResponse =
          http.send(preflight, HttpResponse.BodyHandlers.discarding());
      assertThat(preflightResponse.statusCode()).isEqualTo(200);
      assertThat(preflightResponse.headers().firstValue("Access-Control-Allow-Origin"))
          .contains(origin);
      assertThat(preflightResponse.headers().firstValue("Access-Control-Allow-Credentials"))
          .contains("true");
      assertThat(preflightResponse.headers().firstValue("Access-Control-Allow-Headers").orElse(""))
          .contains("x-xsrf-token");

      HttpRequest actual =
          HttpRequest.newBuilder(URI.create(baseUrl() + "/products"))
              .header("Origin", origin)
              .GET()
              .build();
      HttpResponse<Void> actualResponse = http.send(actual, HttpResponse.BodyHandlers.discarding());
      assertThat(actualResponse.statusCode()).isEqualTo(401);
      assertThat(actualResponse.headers().firstValue("Access-Control-Allow-Origin"))
          .contains(origin);
      assertThat(actualResponse.headers().firstValue("Access-Control-Allow-Credentials"))
          .contains("true");
    }
  }

  @Test
  void persistsErpDataAndServesTheOperationalApiFlow() throws Exception {
    assertRequestWithoutTokenIsUnauthorized();
    assertRequestWithInvalidTokenIsUnauthorized();
    assertRequestWithInvalidClaimsIsUnauthorized();
    JsonNode apiDocs = get("/v3/api-docs", 200);
    assertThat(apiDocs.path("info").path("version").asString()).isEqualTo("3.1.0");
    assertThat(apiDocs.path("paths").has("/flows/current")).isTrue();
    assertThat(apiDocs.path("paths").has("/flows/analyze-login")).isTrue();
    assertThat(apiDocs.at("/paths/~1flows~1analyze-login/post/deprecated").asBoolean()).isTrue();
    assertThat(apiDocs.path("paths").path("/profile").has("patch")).isTrue();
    seedAuthenticatedUser();
    ERP_RESPONSE.set(initialErpPayload());

    JsonNode products = get("/products", 200);
    assertThat(products.size()).isEqualTo(3);
    assertThat(product("SKU-HIGH:STORE-1").path("current_stock").asInt()).isEqualTo(8);
    assertThat(products.get(0).path("name").asString()).isEqualTo("Juice");
    assertThat(products.get(1).path("name").asString()).isEqualTo("Milk");
    assertThat(get("/products?category=Dairy", 200).size()).isEqualTo(1);
    assertThat(get("/products?branch=STORE-1", 200).size()).isEqualTo(2);
    assertThat(get("/products?status=true", 200).size()).isEqualTo(3);
    assertThat(get("/products?status=false", 200).size()).isZero();
    assertThat(get("/branches", 200).size()).isEqualTo(2);
    JsonNode branches = get("/branches", 200);
    assertThat(branches.get(0).path("store_id").isNumber()).isTrue();
    assertThat(branches.get(0).path("name").asString()).isEqualTo("North Branch");
    assertThat(branches.get(1).path("name").asString()).isEqualTo("South Branch");
    String replacementManagerId = assertUserManagementRoutes();
    assertCookieAuthenticationAndCsrf(replacementManagerId);
    assertThat(get("/products/SKU-HIGH:STORE-1", 200).path("id").asString())
        .isEqualTo("SKU-HIGH:STORE-1");
    assertThat(get("/erp-integration/status", 200).path("status").asString())
        .isEqualTo("CONNECTED");

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
    shouldRejectSnapshot(validSnapshot.replaceFirst("REGION-NORTH", "REGION-CONFLICT"));

    JsonNode insufficient = post("/flows/analyze", "{\"product_id\":\"SKU-HIGH:STORE-1\"}", 201);
    assertThat(insufficient.path("status").asString()).isEqualTo("INSUFFICIENT_DATA");
    assertThat(insufficient.path("flow_type").isNull()).isTrue();
    seedComparableProducts();
    String highFlowId = analyze("SKU-HIGH:STORE-1", "HIGH");
    String mediumFlowId = analyze("SKU-MEDIUM:STORE-1", "MEDIUM");
    String lowFlowId = analyze("SKU-LOW:South Branch", "LOW");
    JsonNode highFlow = get("/flows?product_id=SKU-HIGH:STORE-1", 200).get(0);
    assertThat(highFlow.path("product_id").asString()).isEqualTo("SKU-HIGH:STORE-1");
    assertThat(highFlow.path("flow_type").asString()).isEqualTo("HIGH");
    assertThat(highFlow.path("is_current").asBoolean()).isTrue();
    assertThat(
            post("/chat", "{\"message\":\"Which products need a promotion?\"}", 503)
                .path("error")
                .asText())
        .isEqualTo("AI_NOT_CONFIGURED");
    assertThat(get("/flows?flow_type=HIGH", 200).size()).isEqualTo(1);
    assertThat(get("/flows?product_id=SKU-HIGH:STORE-1&status=ANALYZED", 200).size()).isEqualTo(1);

    long north = get("/branches", 200).get(0).path("store_id").asLong();
    long south = get("/branches", 200).get(1).path("store_id").asLong();
    seedOperationalActor(1006L, "EMPLOYEE", north);
    seedOperationalActor(1007L, "EMPLOYEE", south);
    seedOperationalActor(1008L, "MANAGER", south);
    String employeeNorth = tokenFor(1006L, "operational1006@test.example");
    String employeeSouth = tokenFor(1007L, "operational1007@test.example");
    String managerSouth = tokenFor(1008L, "operational1008@test.example");
    String managerNorth =
        tokenFor(Long.parseLong(replacementManagerId), "replacement@test.example");
    String orderBody =
        "{\"flow_id\":\""
            + highFlowId
            + "\",\"action_type\":\"ORDER\",\"quantity\":20,\"desired_order_date\":\""
            + LocalDate.now().plusDays(5)
            + "\"}";
    post("/actions/generate", orderBody, 403);
    postAs(
        "/actions/generate",
        orderBody.replace("\"quantity\":20", "\"quantity\":20.5"),
        400,
        employeeNorth);
    JsonNode order = postAs("/actions/generate", orderBody, 201, employeeNorth);
    assertThat(postAs("/actions/generate", orderBody, 201, employeeNorth)).isEqualTo(order);
    assertThat(order.path("generated_actions").get(0).path("status").asString())
        .isEqualTo("GENERATED");
    assertThat(order.path("generated_actions").get(0).path("desired_order_date").asString())
        .isEqualTo(LocalDate.now().plusDays(5).toString());
    String newerHighFlowId = analyze("SKU-HIGH:STORE-1", "HIGH");
    postAs("/actions/generate", orderBody.replace(highFlowId, newerHighFlowId), 409, employeeNorth);
    String promotionBody =
        "{\"flow_id\":\""
            + lowFlowId
            + "\",\"action_type\":\"PROMOTION\",\"discount_percentage\":15,\"promotion_duration_days\":7}";
    postAs(
        "/actions/generate",
        promotionBody.replace("\"discount_percentage\":15", "\"discount_percentage\":15.123"),
        400,
        employeeSouth);
    JsonNode promotion = postAs("/actions/generate", promotionBody, 201, employeeSouth);
    assertThat(order.path("generated_actions").get(0).path("quantity").asInt()).isEqualTo(20);
    assertThat(promotion.path("generated_actions").get(0).path("discount_percentage").asInt())
        .isEqualTo(15);
    postAs("/actions/generate", promotionBody, 403, tokenFor(1004L, "regional@test.example"));
    postAs("/actions/generate", promotionBody, 404, employeeNorth);
    JsonNode noAction =
        postAs("/actions/generate", "{\"flow_id\":\"" + mediumFlowId + "\"}", 201, employeeNorth);
    assertThat(noAction.path("generated_actions").size()).isZero();
    String orderId = order.path("generated_actions").get(0).path("id").asString();
    String promotionId = promotion.path("generated_actions").get(0).path("id").asString();
    patchAs("/actions/" + promotionId + "/status", "{\"status\":\"APPROVED\"}", 403, employeeSouth);
    patchAs("/actions/" + promotionId + "/status", "{\"status\":\"APPROVED\"}", 409, managerSouth);
    patchAs(
        "/actions/" + promotionId + "/status",
        "{\"status\":\"IN_EMPLOYEE_TRIAGE\"}",
        200,
        employeeSouth);
    patchAs(
        "/actions/" + promotionId + "/status",
        "{\"status\":\"SENT_TO_MANAGER\"}",
        200,
        employeeSouth);
    patchAs("/actions/" + promotionId + "/status", "{\"status\":\"APPROVED\"}", 200, managerSouth);
    patchAs(
        "/actions/" + promotionId + "/status",
        "{\"status\":\"SENT_TO_MANAGER\"}",
        409,
        employeeSouth);
    patchAs(
        "/actions/" + orderId + "/status", "{\"status\":\"SENT_TO_MANAGER\"}", 200, employeeNorth);
    patchAs(
        "/actions/" + orderId + "/status",
        "{\"status\":\"REJECTED\",\"justification\":\"Demand changed\"}",
        200,
        managerNorth);
    assertThat(get("/actions?status=APPROVED&action_type=PROMOTION", 200).size()).isEqualTo(1);
    assertThat(get("/dashboard/summary", 200).path("active_actions").asInt()).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM suggestion_log", Integer.class))
        .isEqualTo(7);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM suggestion_decision", Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM suggestion_triage", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT final_batch_count FROM suggestion_decision WHERE suggestion_id = ?",
                Integer.class,
                Long.parseLong(orderId)))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT final_discount_percentage FROM suggestion_decision WHERE suggestion_id = ?",
                java.math.BigDecimal.class,
                Long.parseLong(promotionId)))
        .isEqualByComparingTo("15");
    postAs("/actions/generate", orderBody.replace(highFlowId, newerHighFlowId), 201, employeeNorth);
    String loginBody = "{\"login_id\":\"33ed01e2-813b-4b24-a0e2-29c11be24562\"}";
    Integer analysisCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM product_analysis", Integer.class);
    post("/flows/analyze-login", loginBody, 200);
    post("/flows/analyze-login", loginBody, 200);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_analysis", Integer.class))
        .isEqualTo(analysisCount);
    assertScheduledAnalysisPersistenceAndConcurrency();
    assertScheduledAnalysisRollbackAndRetry();
    JsonNode current = get("/flows/current", 200);
    assertThat(current.size()).isEqualTo(15);
    assertThat(current.get(0).path("is_current").asBoolean()).isTrue();
    postAs("/actions/generate", orderBody, 409, employeeNorth);
    assertThat(
            patchAs(
                    "/profile",
                    "{\"name\":\"Updated Employee\",\"profile_photo_url\":\"https://images.example.com/photo.jpg\"}",
                    200,
                    employeeNorth)
                .path("name")
                .asString())
        .isEqualTo("Updated Employee");
    Instant fixedProfileTimestamp = Instant.parse("2000-01-01T00:00:00Z");
    jdbc.update(
        "UPDATE user_account SET updated_at = ? WHERE id = ?",
        java.time.OffsetDateTime.ofInstant(fixedProfileTimestamp, java.time.ZoneOffset.UTC),
        1006L);
    patchAs(
        "/profile",
        "{\"name\":\"Updated Employee\",\"profile_photo_url\":\"https://images.example.com/photo.jpg\"}",
        200,
        employeeNorth);
    java.time.OffsetDateTime profileUpdatedAt =
        jdbc.queryForObject(
            "SELECT updated_at FROM user_account WHERE id = ?",
            java.time.OffsetDateTime.class,
            1006L);
    assertThat(profileUpdatedAt.toInstant()).isAfter(fixedProfileTimestamp);
    patchAs("/profile", "{\"role\":\"ADMIN\"}", 400, employeeNorth);
    patchAs("/profile", "{\"profile_photo_url\":\"javascript:invalid\"}", 400, employeeNorth);
    patchAs("/profile", "{}", 400, employeeNorth);
    assertThat(
            patchAs("/profile", "{\"profile_photo_url\":\"\"}", 200, employeeNorth)
                .path("profile_photo_url")
                .isNull())
        .isTrue();

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

  private void assertScheduledAnalysisPersistenceAndConcurrency() throws Exception {
    try (var blocker = jdbc.getDataSource().getConnection()) {
      blocker.setAutoCommit(false);
      try (var statement = blocker.createStatement()) {
        statement.execute("SELECT pg_advisory_xact_lock(74192001)");
      }
      var running = java.util.concurrent.CompletableFuture.supplyAsync(dailyAnalysis::runDaily);
      try {
        org.awaitility.Awaitility.await()
            .atMost(java.time.Duration.ofSeconds(10))
            .until(
                () ->
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM ml_execution WHERE model_version = "
                                + "'SALES_PERCENTILES_V1_DAILY' AND status = 'STARTED'",
                            Integer.class)
                        == 1);
        assertThat(dailyAnalysis.runDaily()).isFalse();
      } finally {
        blocker.rollback();
      }
      assertThat(running.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
    assertThat(dailyAnalysis.runDaily()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_analysis pa JOIN ml_execution me "
                    + "ON me.id = pa.ml_execution_id WHERE me.model_version = "
                    + "'SALES_PERCENTILES_V1_DAILY' AND me.status = 'COMPLETED'",
                Integer.class))
        .isEqualTo(15);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_analysis WHERE ml_execution_id IS NULL",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM ml_execution WHERE model_version = 'SALES_PERCENTILES_V1_DAILY'",
                Integer.class))
        .isEqualTo(1);
  }

  private void assertScheduledAnalysisRollbackAndRetry() {
    int before = jdbc.queryForObject("SELECT COUNT(*) FROM product_analysis", Integer.class);
    var nextDay =
        java.time.Clock.offset(
            java.time.Clock.system(java.time.ZoneId.of("America/Sao_Paulo")),
            java.time.Duration.ofDays(1));
    var failedFlows = org.mockito.Mockito.mock(com.quistock.ds_backend.service.FlowService.class);
    org.mockito.Mockito.when(
            failedFlows.analyze(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()))
        .thenAnswer(
            invocation -> {
              analysisFlows.analyze(
                  invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
              throw new IllegalStateException("Simulated failure after writing an analysis");
            });
    var failing =
        new com.quistock.ds_backend.service.DailyAnalysisService(
            analysisExecutions,
            analysisProducts,
            failedFlows,
            projectionLock,
            nextDay,
            transactions);
    assertThatThrownBy(failing::runDaily).hasMessageContaining("Simulated failure");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_analysis", Integer.class))
        .isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM ml_execution WHERE status = 'FAILED'", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_analysis pa JOIN ml_execution me "
                    + "ON me.id = pa.ml_execution_id WHERE me.status = 'FAILED'",
                Integer.class))
        .isZero();
    var retry =
        new com.quistock.ds_backend.service.DailyAnalysisService(
            analysisExecutions,
            analysisProducts,
            analysisFlows,
            projectionLock,
            nextDay,
            transactions);
    assertThat(retry.runDaily()).isTrue();
    assertThat(retry.runDaily()).isFalse();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_analysis", Integer.class))
        .isEqualTo(before + 15);
  }

  private void seedComparableProducts() {
    for (String sku : java.util.List.of("SKU-HIGH", "SKU-MEDIUM", "SKU-LOW")) {
      for (int index = 1; index <= 4; index++) {
        Long id =
            jdbc.queryForObject(
                """
            INSERT INTO product (erp_id, sku, name, category_id, sale_price)
            SELECT ?, ?, 'Comparable product', category_id, 10 FROM product WHERE sku = ? RETURNING id
            """,
                Long.class,
                sku + "-PEER" + index,
                sku + "-PEER" + index,
                sku);
        int sales = "SKU-MEDIUM".equals(sku) ? index * index : index;
        jdbc.update(
            """
            INSERT INTO product_store (product_id, store_id, sales_7d, sales_30d, sale_price, unit_cost)
            SELECT ?, store_id, ?, 30, 10, 5 FROM product_store ps
            JOIN product p ON p.id = ps.product_id WHERE p.sku = ?
            """,
            id,
            sales,
            sku);
      }
    }
  }

  private void seedOperationalActor(long id, String role, long store) {
    jdbc.update(
        """
        INSERT INTO user_account (id, role_id, name, email, password_hash)
        SELECT ?, id, 'Operational actor', ?, 'test-hash' FROM role WHERE code = ?
        """,
        id,
        "operational" + id + "@test.example",
        role);
    jdbc.update("INSERT INTO user_store (user_id, store_id) VALUES (?, ?)", id, store);
  }

  private String analyze(String productId, String expectedType) throws Exception {
    JsonNode flow = post("/flows/analyze", "{\"product_id\":\"" + productId + "\"}", 201);
    assertThat(flow.path("flow_type").asString()).isEqualTo(expectedType);
    return flow.path("id").asString();
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
    jdbc.update(
        "INSERT INTO user_account (id, role_id, name, email, password_hash) VALUES "
            + "(?, (SELECT id FROM role WHERE code = 'ADMIN'), ?, ?, ?)",
        TEST_USER_ID,
        "Mobile User",
        "mobile@test.example",
        "test-hash");
    jdbc.update(
        "INSERT INTO user_account (id, role_id, name, email, password_hash) "
            + "VALUES (?, (SELECT id FROM role WHERE code = 'ADMIN'), ?, ?, ?)",
        1002L,
        "Admin User",
        "admin@test.example",
        "test-hash");
    jdbc.update(
        "INSERT INTO user_account (id, role_id, name, email, password_hash) "
            + "VALUES (?, (SELECT id FROM role WHERE code = 'MANAGER'), ?, ?, ?)",
        1003L,
        "Manager User",
        "manager@test.example",
        "test-hash");
  }

  private String assertUserManagementRoutes() throws Exception {
    String adminToken = tokenFor(1002L, "admin@test.example");
    String managerToken = tokenFor(1003L, "manager@test.example");
    JsonNode branches = get("/branches", 200);
    long storeOne = branches.get(0).path("store_id").asLong();
    long storeTwo = branches.get(1).path("store_id").asLong();
    jdbc.update("INSERT INTO user_store (user_id, store_id) VALUES (1003, ?)", storeOne);
    long regionId =
        jdbc.queryForObject("SELECT region_id FROM store WHERE id = ?", Long.class, storeOne);
    jdbc.update(
        "INSERT INTO user_account (id, role_id, name, email, password_hash) "
            + "SELECT 1004, id, 'Regional Manager', 'regional@test.example', 'test-hash' "
            + "FROM role WHERE code = 'REGIONAL_MANAGER'");
    jdbc.update(
        "INSERT INTO region_manager_assignment (user_id, region_id) VALUES (1004, ?)", regionId);
    String regionalToken = tokenFor(1004L, "regional@test.example");

    assertThat(getAs("/profile", 200, managerToken).path("role").asString()).isEqualTo("MANAGER");
    JsonNode managerBranches = getAs("/branches", 200, managerToken);
    assertThat(managerBranches.size()).isEqualTo(1);
    assertThat(managerBranches.get(0).path("store_id").asLong()).isEqualTo(storeOne);
    assertThat(getAs("/managers", 200, adminToken).size()).isEqualTo(1);
    assertThat(getAs("/managers/1003", 200, regionalToken).path("email").asString())
        .isEqualTo("manager@test.example");
    getAs("/managers", 403, managerToken);
    getAs("/regions", 200, managerToken);
    assertThat(getAs("/regions", 200, regionalToken).size()).isEqualTo(1);
    JsonNode regionalBranches = getAs("/branches", 200, regionalToken);
    assertThat(regionalBranches.size()).isEqualTo(1);
    assertThat(regionalBranches.get(0).path("store_id").asLong()).isEqualTo(storeOne);
    assertThat(regionalBranches.get(0).path("region_id").asLong()).isEqualTo(regionId);

    JsonNode employee =
        postAs(
            "/team-members",
            """
            {"name":"Store Employee","email":"employee@test.example","password":"GenericPass123",
             "role":"EMPLOYEE","store_id":%d}
            """
                .formatted(storeOne),
            201,
            managerToken);
    String employeeId = employee.path("id").asString();
    String employeeToken = tokenFor(Long.parseLong(employeeId), "employee@test.example");
    JsonNode employeeBranches = getAs("/branches", 200, employeeToken);
    assertThat(employeeBranches.size()).isEqualTo(1);
    assertThat(employeeBranches.get(0).path("store_id").asLong()).isEqualTo(storeOne);
    getAs("/managers", 403, employeeToken);
    getAs("/team-members", 403, employeeToken);
    getAs("/regions", 403, tokenFor(Long.parseLong(employeeId), "employee@test.example"));
    assertThat(getAs("/team-members/" + employeeId, 200, managerToken).path("id").asString())
        .isEqualTo(employeeId);
    assertThat(employee.path("role").asString()).isEqualTo("EMPLOYEE");
    assertThat(employee.has("password")).isFalse();
    assertThat(employee.has("password_hash")).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash LIKE '$2a$12$%%' OR password_hash LIKE '$2b$12$%%' "
                    + "FROM user_account WHERE id = ?",
                Boolean.class, Long.parseLong(employeeId)))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT created_by_id FROM user_account WHERE id = ?",
                Long.class,
                Long.parseLong(employeeId)))
        .isEqualTo(1003L);
    postAs(
        "/team-members",
        """
        {"name":"Missing Store","email":"missing-store@test.example",
         "password":"GenericPass123","role":"EMPLOYEE"}
        """,
        400,
        managerToken);
    postAs(
        "/team-members",
        """
        {"name":"Duplicate Employee","email":"EMPLOYEE@test.example",
         "password":"GenericPass123","role":"EMPLOYEE","store_id":%d}
        """
            .formatted(storeOne),
        409,
        managerToken);
    postAs(
        "/team-members",
        """
        {"name":"Missing Store","email":"missing-store-id@test.example",
         "password":"GenericPass123","role":"EMPLOYEE","store_id":999999}
        """,
        404,
        managerToken);

    JsonNode regions = getAs("/regions", 200, managerToken);
    getAs("/regions", 200, adminToken);
    assertThat(regions.size()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM region_type WHERE code IN ('REGION-NORTH', 'REGION-SOUTH')",
                Integer.class))
        .isEqualTo(2);
    postAs(
        "/team-members",
        """
        {"name":"Regional Manager","email":"new-regional@test.example","password":"GenericPass123",
         "role":"REGIONAL_MANAGER","region_id":%d}
        """
            .formatted(regionId),
        400,
        managerToken);
    patchAs("/team-members/1004", "{\"status\":\"INACTIVE\"}", 404, managerToken);
    postAs(
        "/team-members",
        """
        {"name":"Invalid Employee","email":"bad-assignment@test.example","password":"GenericPass123",
         "role":"EMPLOYEE","store_id":%d,"region_id":%d}
        """
            .formatted(storeOne, regionId),
        400,
        managerToken);
    getAs("/team-members/1002", 404, managerToken);
    patchAs("/team-members/" + employeeId, "{\"store_id\":" + storeTwo + "}", 403, managerToken);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_store WHERE user_id = ? AND active = TRUE",
                Integer.class,
                Long.parseLong(employeeId)))
        .isEqualTo(1);
    patchAs("/team-members/" + employeeId, "{\"status\":\"INACTIVE\"}", 200, managerToken);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_store WHERE user_id = ? AND active = TRUE",
                Integer.class,
                Long.parseLong(employeeId)))
        .isZero();
    patchAs(
        "/team-members/" + employeeId,
        "{\"status\":\"ACTIVE\",\"store_id\":" + storeOne + "}",
        200,
        managerToken);
    patchAs("/team-members/" + employeeId, "{\"status\":\"UNKNOWN\"}", 400, managerToken);
    patchAs("/team-members/" + employeeId, "{\"role\":\"MANAGER\"}", 400, managerToken);

    JsonNode replacement =
        postAs(
            "/managers",
            """
            {"name":"Replacement Manager","email":"replacement@test.example",
             "password":"GenericPass123","store_id":%d}
            """
                .formatted(storeOne),
            201,
            regionalToken);
    String replacementId = replacement.path("id").asString();
    assertThat(replacement.has("password_hash")).isFalse();
    postAs(
        "/managers",
        """
        {"name":"Duplicate Email","email":"REPLACEMENT@test.example",
         "password":"GenericPass123","store_id":%d}
        """
            .formatted(storeOne),
        409,
        adminToken);
    patchAs("/managers/1003", "{\"role\":\"ADMIN\"}", 400, adminToken);
    assertThat(
            patchAs("/managers/1003", "{\"status\":\"INACTIVE\"}", 400, regionalToken)
                .path("error")
                .asString())
        .isEqualTo("INVALID_REQUEST");
    patchAs(
        "/managers/1003",
        "{\"status\":\"INACTIVE\",\"replacement_manager_id\":" + replacementId + "}",
        200,
        regionalToken);
    assertThat(
            jdbc.queryForObject(
                "SELECT created_by_id FROM user_account WHERE id = ?",
                Long.class,
                Long.parseLong(employeeId)))
        .isEqualTo(1003L);
    getAs("/team-members", 403, managerToken);
    getAs("/profile", 403, managerToken);
    assertThat(
            getAs(
                    "/team-members",
                    200,
                    tokenFor(Long.parseLong(replacementId), "replacement@test.example"))
                .size())
        .isEqualTo(1);
    return replacementId;
  }

  private void assertCookieAuthenticationAndCsrf(String managerId) throws Exception {
    String managerToken = tokenFor(Long.parseLong(managerId), "replacement@test.example");
    HttpResponse<String> csrfResponse =
        http.send(
            HttpRequest.newBuilder(URI.create(baseUrl() + "/csrf"))
                .header("Cookie", "access_token=" + managerToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(csrfResponse.statusCode()).isEqualTo(200);
    String token = objectMapper.readTree(csrfResponse.body()).path("token").asString();
    String csrfCookie =
        csrfResponse.headers().allValues("set-cookie").stream()
            .filter(value -> value.startsWith("XSRF-TOKEN="))
            .map(value -> value.substring(0, value.indexOf(';')))
            .findFirst()
            .orElseThrow();
    String cookies = "access_token=" + managerToken + "; " + csrfCookie;

    HttpResponse<String> profileResponse =
        http.send(
            HttpRequest.newBuilder(URI.create(baseUrl() + "/profile"))
                .header("Cookie", cookies)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(profileResponse.statusCode()).isEqualTo(200);

    HttpRequest missingCsrf =
        HttpRequest.newBuilder(URI.create(baseUrl() + "/team-members"))
            .header("Cookie", cookies)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"name\":\"Cookie User\",\"email\":\"cookie-user@test.example\","
                        + "\"password\":\"GenericPass123\",\"role\":\"EMPLOYEE\",\"store_id\":1}"))
            .build();
    assertThat(http.send(missingCsrf, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(403);

    long activeStore = get("/branches", 200).get(0).path("store_id").asLong();
    HttpRequest validCsrf =
        HttpRequest.newBuilder(URI.create(baseUrl() + "/team-members"))
            .header("Cookie", cookies)
            .header("X-XSRF-TOKEN", token)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"name\":\"Cookie User\",\"email\":\"cookie-user@test.example\","
                        + "\"password\":\"GenericPass123\",\"role\":\"EMPLOYEE\",\"store_id\":"
                        + activeStore
                        + "}"))
            .build();
    assertThat(http.send(validCsrf, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(201);
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port + System.getProperty("routing.test.context", "");
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

  private JsonNode getAs(String path, int expectedStatus, String token) throws Exception {
    return exchangeAs(HttpMethod.GET, path, null, expectedStatus, token);
  }

  private JsonNode post(String path, String body, int expectedStatus) throws Exception {
    return exchange(HttpMethod.POST, path, body, expectedStatus);
  }

  private JsonNode postAs(String path, String body, int expectedStatus, String token)
      throws Exception {
    return exchangeAs(HttpMethod.POST, path, body, expectedStatus, token);
  }

  private JsonNode patch(String path, String body, int expectedStatus) throws Exception {
    return exchange(HttpMethod.PATCH, path, body, expectedStatus);
  }

  private JsonNode patchAs(String path, String body, int expectedStatus, String token)
      throws Exception {
    return exchangeAs(HttpMethod.PATCH, path, body, expectedStatus, token);
  }

  private JsonNode exchange(HttpMethod method, String path, String body, int expectedStatus)
      throws Exception {
    return exchangeAs(method, path, body, expectedStatus, validToken());
  }

  private JsonNode exchangeAs(
      HttpMethod method, String path, String body, int expectedStatus, String token)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:"
                        + port
                        + System.getProperty("routing.test.context", "")
                        + path))
            .header("Authorization", "Bearer " + token);
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
        "region_id":"REGION-NORTH",
        "certificado_qualidade":true,"data_entrada":"2026-08-20T08:00:00Z",
        "vendas_7d":"7","vendas_30d":"30","estoque_minimo":"10","lead_time_dias":"5"
      },
      {
        "id":"HIGH-2","codigo_produto_erp":"SKU-HIGH","nome_produto":"Milk",
        "categoria":"Dairy","num_lote":"H-2","data_validade":"%s",
        "quantidade":"5","preco":"8.10","custo":"5.40",
        "codigo_filial_erp":"STORE-1","filial":"North Branch",
        "region_id":"REGION-NORTH",
        "certificado_qualidade":true,"data_entrada":"2026-08-21T08:00:00Z",
        "vendas_7d":"7","vendas_30d":"30","estoque_minimo":"10","lead_time_dias":"5"
      },
      {
        "id":"MED-1","codigo_produto_erp":"SKU-MEDIUM","nome_produto":"Oats",
        "categoria":"Cereals","num_lote":"M-1","data_validade":"%s",
        "quantidade":"50","preco":"4.50","custo":"2.70",
        "codigo_filial_erp":"STORE-1","filial":"North Branch",
        "region_id":"REGION-NORTH",
        "certificado_qualidade":true,"data_entrada":"2026-08-22T08:00:00Z",
        "vendas_7d":"7","vendas_30d":"30","estoque_minimo":"20","lead_time_dias":"5"
      },
      {
        "id":"LOW-1","codigo_produto_erp":"SKU-LOW","nome_produto":"Juice",
        "categoria":"Beverages","num_lote":"L-1","data_validade":"%s",
        "quantidade":"100","preco":"3.50","custo":"1.90",
        "filial":"South Branch","region_id":"REGION-SOUTH","certificado_qualidade":false,
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
      "region_id":"REGION-NORTH",
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
    return tokenFor(TEST_USER_ID, "mobile@test.example");
  }

  private static String tokenFor(long userId, String email) {
    return signedToken(
        TEST_ISSUER, TEST_AUDIENCE, Long.toString(userId), email, Instant.now().plusSeconds(300));
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
