package com.quistock.ds_backend.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiClientConfig {
  @Bean(destroyMethod = "close")
  public HttpClient aiHttpClient(@Value("${ai.api.connect-timeout-ms:5000}") int connectTimeout) {
    if (connectTimeout <= 0) {
      throw new IllegalStateException("AI connect timeout must be positive.");
    }
    return HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeout)).build();
  }

  @Bean
  public RestClient aiRestClient(
      HttpClient aiHttpClient,
      @Value("${ai.api.base-url:}") String baseUrl,
      @Value("${ai.api.read-timeout-ms:60000}") int readTimeout) {
    if (!baseUrl.isBlank()) {
      DeploymentSettingsValidator.requireHttpUrl("AI_API_BASE_URL", baseUrl);
    }
    if (readTimeout <= 0) {
      throw new IllegalStateException("AI read timeout must be positive.");
    }
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(aiHttpClient);
    factory.setReadTimeout(Duration.ofMillis(readTimeout));
    RestClient.Builder builder = RestClient.builder().requestFactory(factory);
    return baseUrl.isBlank() ? builder.build() : builder.baseUrl(baseUrl).build();
  }
}
