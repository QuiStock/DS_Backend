package com.quistock.ds_backend.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.import=",
      "spring.datasource.url=jdbc:h2:mem:health_test;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=false",
      "erp.sync.enabled=false",
      "auth.jwt.issuer=https://auth.test.example",
      "auth.jwt.jwk-set-uri=http://127.0.0.1:8090/.well-known/jwks.json",
      "auth.jwt.audience=quistock-api"
    })
@TestPropertySource(locations = "file:src/main/resources/application.properties")
class HealthEndpointsTest {
  @Value("${local.server.port}")
  private int port;

  @MockitoSpyBean private BackendDependencyHealthIndicator dependencies;

  @Test
  @SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
  void publicProbesSeparateLivenessAndCacheDependencyFailures() throws Exception {
    doReturn(Health.down().build()).when(dependencies).dependenciesHealth(any());
    clearInvocations(dependencies);
    try (var client = HttpClient.newHttpClient()) {
      assertThat(get(client, "/health/liveness").statusCode()).isEqualTo(200);
      verify(dependencies, never()).dependenciesHealth(any());
      assertThat(get(client, "/health/readiness").statusCode()).isEqualTo(503);
      assertThat(get(client, "/health/readiness").statusCode()).isEqualTo(503);
      verify(dependencies).dependenciesHealth(any());
      assertThat(get(client, "/health").statusCode()).isEqualTo(503);
    }
  }

  private HttpResponse<String> get(HttpClient client, String path)
      throws IOException, InterruptedException {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
