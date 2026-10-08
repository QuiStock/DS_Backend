package com.quistock.ds_backend.config;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
  @Bean
  @SuppressWarnings("PMD.SignatureDeclareThrowsException")
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      BearerTokenResolver cookieBearerTokenResolver,
      CookieCsrfTokenRepository csrfTokenRepository)
      throws Exception {
    RequestMatcher bearerHeaderRequest =
        request -> {
          String authorization = request.getHeader("Authorization");
          return authorization != null
              && authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length());
        };
    return http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers(bearerHeaderRequest))
        .addFilterBefore(
            new CookieCsrfProtectionFilter(csrfTokenRepository),
            BearerTokenAuthenticationFilter.class)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        HttpMethod.GET, "/health", "/health/liveness", "/health/readiness")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/csrf")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .bearerTokenResolver(cookieBearerTokenResolver)
                    .jwt(Customizer.withDefaults()))
        .build();
  }

  @Bean
  BearerTokenResolver cookieBearerTokenResolver() {
    DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
    return request -> {
      String headerToken = headerResolver.resolve(request);
      if (headerToken != null) {
        return headerToken;
      }
      Cookie[] cookies = request.getCookies();
      if (cookies != null) {
        for (Cookie cookie : cookies) {
          if ("access_token".equals(cookie.getName()) && !cookie.getValue().isBlank()) {
            return cookie.getValue();
          }
        }
      }
      return null;
    };
  }

  @Bean
  CookieCsrfTokenRepository csrfTokenRepository(
      @Value("${app.cookie.secure:true}") boolean secureCookie) {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookiePath("/");
    repository.setCookieCustomizer(
        cookie -> cookie.secure(secureCookie).sameSite(secureCookie ? "None" : "Lax"));
    return repository;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOriginPatterns(List.of("*"));
    cors.setAllowCredentials(true);
    cors.setAllowedMethods(List.of("*"));
    cors.setAllowedHeaders(List.of("*"));
    cors.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }
}
