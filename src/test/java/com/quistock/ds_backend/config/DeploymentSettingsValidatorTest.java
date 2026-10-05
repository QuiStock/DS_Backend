package com.quistock.ds_backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DeploymentSettingsValidatorTest {
  @Test
  void acceptsCompleteServiceUrls() {
    assertThatCode(
            () ->
                DeploymentSettingsValidator.requireHttpUrl(
                    "JWKS", "http://api-auth:8080/.well-known/jwks.json"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsMissingAndInvalidUrls() {
    for (String url :
        new String[] {
          "", "/api/jwks", "ftp://auth/keys", "https://", "https://user:pass@auth/keys"
        }) {
      assertThatThrownBy(() -> DeploymentSettingsValidator.requireHttpUrl("JWKS", url))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("JWKS");
    }
  }

  @Test
  void rejectsBlankAudience() {
    assertThatThrownBy(() -> DeploymentSettingsValidator.requireAudience(" "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AUTH_JWT_AUDIENCE");
  }

  @Test
  void requiresErpUrlWhenEnabled() {
    RestClientConfig config = new RestClientConfig();
    assertThatThrownBy(() -> config.erpRestClient("", "/products", true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ERP_API_BASE_URL");
  }

  @Test
  void permitsDisabledErpWithoutUrl() {
    RestClientConfig config = new RestClientConfig();
    assertThatCode(() -> config.erpRestClient("", "/products", false)).doesNotThrowAnyException();
  }

  @Test
  void rejectsErpPathWithHost() {
    RestClientConfig config = new RestClientConfig();
    assertThatThrownBy(() -> config.erpRestClient("http://erp:8080", "//other-host/products", true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ERP_API_PRODUCTS_PATH");
  }
}
