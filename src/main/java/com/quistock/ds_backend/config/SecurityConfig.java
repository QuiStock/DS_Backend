package com.quistock.ds_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  @SuppressWarnings("PMD.SignatureDeclareThrowsException")
  SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(HttpMethod.GET, "/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
        .build();
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${auth.jwt.jwk-set-uri}") String jwkSetUri,
      @Value("${auth.jwt.issuer}") String issuer,
      @Value("${auth.jwt.audience}") String audience) {
    DeploymentSettingsValidator.requireHttpUrl("AUTH_JWT_JWK_SET_URI", jwkSetUri);
    DeploymentSettingsValidator.requireHttpUrl("AUTH_JWT_ISSUER", issuer);
    DeploymentSettingsValidator.requireAudience(audience);
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithm(SignatureAlgorithm.RS256).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            audienceValidator(audience),
            subjectValidator(),
            emailValidator()));
    return decoder;
  }

  private OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
    return jwt -> {
      var tokenAudience = jwt.getAudience();
      return tokenAudience != null && tokenAudience.contains(audience)
          ? OAuth2TokenValidatorResult.success()
          : invalidToken("The access token is not intended for this API.");
    };
  }

  private OAuth2TokenValidator<Jwt> subjectValidator() {
    return jwt -> {
      try {
        return Long.parseLong(jwt.getSubject()) > 0
            ? OAuth2TokenValidatorResult.success()
            : invalidToken("The access token subject must be a positive SQL user ID.");
      } catch (NumberFormatException exception) {
        return invalidToken("The access token subject must be a positive SQL user ID.");
      }
    };
  }

  private OAuth2TokenValidator<Jwt> emailValidator() {
    return jwt -> {
      Object emailClaim = jwt.getClaims().get("email");
      return emailClaim instanceof String email && !email.isBlank()
          ? OAuth2TokenValidatorResult.success()
          : invalidToken("The access token must contain an email claim.");
    };
  }

  private OAuth2TokenValidatorResult invalidToken(String description) {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
  }
}
