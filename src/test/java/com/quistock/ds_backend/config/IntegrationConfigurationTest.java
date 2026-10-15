package com.quistock.ds_backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class IntegrationConfigurationTest {
  @ParameterizedTest
  @CsvSource({"false,None", "true,invalid", "false,lax"})
  void rejectsInvalidCsrfCookieSettings(boolean secure, String sameSite) {
    assertThatThrownBy(
            () -> new SecurityConfig().apiSecurityFilterChain(null, null, secure, sameSite))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SameSite");
  }

  @Test
  void rejectsWildcardCorsWithCredentials() {
    assertThatThrownBy(() -> new SecurityConfig().corsConfigurationSource("*"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parsesExactCorsOriginsAndDeniesUnconfiguredCrossOriginAccess() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/csrf");
    var source =
        new SecurityConfig()
            .corsConfigurationSource(" https://web.example, ,http://localhost:5173 ");
    var cors = source.getCorsConfiguration(request);
    assertThat(cors.getAllowedOrigins())
        .containsExactly("https://web.example", "http://localhost:5173");
    assertThat(cors.checkOrigin("https://evil.example")).isNull();
    assertThat(
            new SecurityConfig()
                .corsConfigurationSource("")
                .getCorsConfiguration(request)
                .checkOrigin("https://web.example"))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void requiresPositiveConnectionTimeout(int timeout) {
    assertThatThrownBy(() -> new AiClientConfig().aiHttpClient(timeout))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void requiresPositiveReadTimeout(int timeout) {
    AiClientConfig config = new AiClientConfig();
    try (var client = config.aiHttpClient(5000)) {
      assertThatThrownBy(() -> config.aiRestClient(client, "http://ai.example", timeout))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void rejectsInvalidAiUrlAndAllowsAnUnconfiguredAi() {
    AiClientConfig config = new AiClientConfig();
    try (var client = config.aiHttpClient(5000)) {
      assertThatThrownBy(() -> config.aiRestClient(client, "file:///private", 5000))
          .isInstanceOf(IllegalStateException.class);
      assertThat(config.aiRestClient(client, "", 5000)).isNotNull();
    }
  }
}
