package com.quistock.ds_backend.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

  @Bean
  public RestClient erpRestClient(
      @Value("${erp.api.base-url:}") String baseUrl,
      @Value("${erp.api.products-path:/products}") String productsPath,
      @Value("${erp.sync.enabled:true}") boolean enabled) {
    if (enabled || !baseUrl.isBlank()) {
      DeploymentSettingsValidator.requireHttpUrl("ERP_API_BASE_URL", baseUrl);
    }
    if (!productsPath.startsWith("/") || productsPath.startsWith("//")) {
      throw new IllegalStateException("ERP_API_PRODUCTS_PATH must be an absolute path.");
    }
    return baseUrl.isBlank()
        ? RestClient.builder().build()
        : RestClient.builder().baseUrl(baseUrl).build();
  }

  @Bean
  public Clock applicationClock() {
    return Clock.systemDefaultZone();
  }
}
