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
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Component("dependenciesHealthIndicator")
public class BackendDependencyHealthIndicator extends DependencyHealthIndicator {
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(2);
  private final JdbcTemplate sql;
  private final URI jwksUri;
  private final URI erpUri;
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
    // Match the ERP client's path semantics, retaining any base path.
    this.erpUri =
        erpBaseUrl.isBlank()
            ? null
            : new DefaultUriBuilderFactory(erpBaseUrl).uriString(erpProductsPath).build();
    this.erpRequired = erpRequired;
  }

  @Override
  protected boolean dependenciesAvailable() throws IOException, InterruptedException, ParseException {
    sql.queryForObject("SELECT 1", Integer.class);
    HttpResponse<String> jwks = get(jwksUri, HttpResponse.BodyHandlers.ofString());
    if (jwks.statusCode() != 200 || !hasUsableSigningKey(JWKSet.parse(jwks.body()))) {
      return false;
    }
    return !erpRequired || (erpUri != null && get(erpUri, HttpResponse.BodyHandlers.discarding()).statusCode() == 200);
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
          if (rsa.toRSAPublicKey().getModulus().bitLength() >= 2048) {
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
