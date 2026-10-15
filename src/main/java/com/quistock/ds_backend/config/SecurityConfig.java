package com.quistock.ds_backend.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
  @Bean
  @SuppressWarnings("PMD.SignatureDeclareThrowsException")
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      JwtDecoder decoder,
      @Value("${auth.csrf.cookie-secure:true}") boolean secure,
      @Value("${auth.csrf.cookie-same-site:Lax}") String sameSite)
      throws Exception {
    if (!List.of("Lax", "Strict", "None").contains(sameSite)
        || "None".equals(sameSite) && !secure) {
      throw new IllegalStateException(
          "CSRF cookie SameSite must be Lax, Strict or None; None requires Secure.");
    }
    CookieCsrfTokenRepository csrf = new CookieCsrfTokenRepository();
    csrf.setCookieName("XSRF-TOKEN");
    csrf.setHeaderName("X-XSRF-TOKEN");
    csrf.setCookieCustomizer(
        cookie -> cookie.path("/").secure(secure).httpOnly(true).sameSite(sameSite));
    BearerTokenAuthenticationConverter converter = new BearerTokenAuthenticationConverter();
    converter.setBearerTokenResolver(new AccessTokenResolver());
    BearerTokenAuthenticationFilter authentication =
        new BearerTokenAuthenticationFilter(
            new ProviderManager(new JwtAuthenticationProvider(decoder)), converter);
    // The resource-server DSL exempts every resolved token from CSRF, including cookies.
    // Register its standard authentication filter explicitly to keep cookies protected.
    return http.csrf(
            config ->
                config
                    .csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .requireCsrfProtectionMatcher(AccessTokenResolver::requiresCsrf))
        .cors(Customizer.withDefaults())
        .requestCache(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        HttpMethod.GET, "/health", "/health/liveness", "/health/readiness")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(authentication, AnonymousAuthenticationFilter.class)
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origins:}") String origins) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(
        Arrays.stream(origins.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList());
    cors.setAllowCredentials(true);
    cors.validateAllowCredentials();
    cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-XSRF-TOKEN"));
    cors.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }
}
