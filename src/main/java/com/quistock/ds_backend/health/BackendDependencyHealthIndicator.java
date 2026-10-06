package com.quistock.ds_backend.health;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Component("dependenciesHealthIndicator")
public class BackendDependencyHealthIndicator extends DependencyHealthIndicator {
  private static final int RSA_PUBLIC_KEY_MIN_USABLE_BIT_LENGTH_THRESHOLD = 2048;
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(2);
  private final JdbcTemplate sql;
  private final URI jwksUri;
  private final Optional<URI> erpUri;
  private final boolean erpRequired;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(HTTP_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public BackendDependencyHealthIndicator(
      DataSource dataSource,
      @Value("${auth.jwt.jwk-set-uri}") String jwksUrl,
      @Value("${erp.api.base-url:}") String erpBaseUrl,
      @Value("${erp.api.products-path:/products}") String erpProductsPath,
      @Value("${app.health.erp-required:true}") boolean erpRequired,
      @Value("${app.health.timeout-ms:4000}") long timeoutMs) {
    super(timeoutMs);
    this.sql = new JdbcTemplate(dataSource);
    this.sql.setQueryTimeout(2);
    this.jwksUri = URI.create(jwksUrl);
    this.erpRequired = erpRequired;

    // Match the ERP client's path semantics, retaining any base path.
    this.erpUri =
        erpBaseUrl.isBlank()
            ? Optional.empty()
            : Optional.of(
                new DefaultUriBuilderFactory(erpBaseUrl).uriString(erpProductsPath).build());
  }

  @Override
  protected Health dependenciesHealth(AtomicReference<String> dependency)
      throws IOException, InterruptedException, ParseException {
    dependency.set("database");
    sql.queryForObject("SELECT 1", Integer.class);
    dependency.set("jwks");
    var jwks = get(jwksUri, HttpResponse.BodyHandlers.ofString());
    if (jwks.statusCode() != HttpStatus.OK.value()) {
      return unavailable("jwks", "unexpected_http_status")
          .withDetail("httpStatus", jwks.statusCode())
          .build();
    }
    if (!hasUsableSigningKey(JWKSet.parse(jwks.body()))) {
      return unavailable("jwks", "no_usable_signing_key").build();
    }
    if (!erpRequired) {
      return Health.up().build();
    }
    dependency.set("erp");
    if (erpUri.isEmpty()) {
      return unavailable("erp", "not_configured").build();
    }
    int erpStatus = get(erpUri.get(), HttpResponse.BodyHandlers.discarding()).statusCode();
    return erpStatus == HttpStatus.OK.value()
        ? Health.up().build()
        : unavailable("erp", "unexpected_http_status").withDetail("httpStatus", erpStatus).build();
  }

  private Health.Builder unavailable(String dependency, String reason) {
    return Health.down().withDetail("dependency", dependency).withDetail("reason", reason);
  }

  private <T> HttpResponse<T> get(URI uri, HttpResponse.BodyHandler<T> bodyHandler)
      throws IOException, InterruptedException {
    return http.send(
        HttpRequest.newBuilder(uri)
            .timeout(HTTP_TIMEOUT)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .GET()
            .build(),
        bodyHandler);
  }

  private boolean hasUsableSigningKey(JWKSet keys) {
    for (JWK key : keys.getKeys()) {
      if (key instanceof RSAKey rsa && isSigningKey(rsa)) {
        try {
          if (rsa.toRSAPublicKey().getModulus().bitLength()
              >= RSA_PUBLIC_KEY_MIN_USABLE_BIT_LENGTH_THRESHOLD) {
            return true;
          }
        } catch (JOSEException exception) {
          return false;
        }
      }
    }
    return false;
  }

  private boolean isSigningKey(RSAKey rsa) {
    return !rsa.isPrivate()
        && (rsa.getAlgorithm() == null || "RS256".equals(rsa.getAlgorithm().getName()))
        && (rsa.getKeyUse() == null || "sig".equals(rsa.getKeyUse().getValue()));
  }

  @Override
  protected void releaseResources() {
    http.shutdownNow();
  }
}
