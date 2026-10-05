package com.quistock.ds_backend.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class BackendDependencyHealthIndicatorTest {
  private final AtomicInteger jwksStatus = new AtomicInteger(200);
  private final AtomicInteger erpStatus = new AtomicInteger(200);
  private final AtomicReference<String> keys = new AtomicReference<>();
  private HttpServer server;
  private BackendDependencyHealthIndicator indicator;
  private String baseUrl;

  @BeforeEach
  void setup() throws IOException, JOSEException {
    keys.set(new JWKSet(new RSAKeyGenerator(2048).generate().toPublicJWK()).toString());
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/jwks",
        exchange -> {
          byte[] body = keys.get().getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(jwksStatus.get(), body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.createContext(
        "/erp/products",
        exchange -> {
          byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(erpStatus.get(), body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    indicator = createIndicator(true);
  }

  private BackendDependencyHealthIndicator createIndicator(boolean erpRequired) {
    return new BackendDependencyHealthIndicator(
        new DriverManagerDataSource("jdbc:h2:mem:health", "sa", ""),
        baseUrl + "/jwks",
        baseUrl + "/erp",
        "/products",
        erpRequired,
        2000);
  }

  @AfterEach
  void cleanup() {
    indicator.shutdown();
    server.stop(0);
  }

  @Test
  void requiresSqlJwksAndErpAccess() {
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
  }

  @Test
  void doesNotUseCachedJwksAfterAnOutage() {
    indicator.health();
    jwksStatus.set(503);
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
  }

  @Test
  void rejectsEmptyKeySets() {
    keys.set("{\"keys\":[]}");
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
  }

  @Test
  void rejectsMalformedJwks() {
    keys.set("invalid JSON");
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
  }

  @Test
  void requiresErpEvenWithoutScheduledSynchronization() {
    erpStatus.set(503);
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
  }

  @Test
  void allowsAnExplicitlyUnnecessaryErp() {
    indicator.shutdown();
    indicator = createIndicator(false);
    erpStatus.set(503);
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
  }

  @Test
  void recoversWhenDependenciesRecover() {
    erpStatus.set(503);
    indicator.health();
    erpStatus.set(200);
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
  }
}
