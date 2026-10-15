package com.quistock.ds_backend.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.csrf.CsrfFilter;

public class AccessTokenResolver implements BearerTokenResolver {
  private static final String ACCESS_TOKEN_COOKIE = "access_token";
  private static final int MAX_ACCESS_COOKIES = 1;
  private final DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();

  @Override
  public String resolve(HttpServletRequest request) {
    List<Cookie> cookies = accessCookies(request);
    if (cookies.size() > MAX_ACCESS_COOKIES) {
      throw invalidRequest("Multiple access token cookies are not allowed.");
    }
    if (cookies.isEmpty()) {
      return bearer.resolve(request);
    }
    if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
      throw invalidRequest("Use either the access token cookie or Bearer authentication.");
    }
    String token = cookies.getFirst().getValue();
    if (token == null || token.isBlank()) {
      throw new OAuth2AuthenticationException(
          BearerTokenErrors.invalidToken("Empty access token."));
    }
    return token;
  }

  static boolean requiresCsrf(HttpServletRequest request) {
    if (!CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
        || request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
      return false;
    }
    return !accessCookies(request).isEmpty();
  }

  private static List<Cookie> accessCookies(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    return cookies == null
        ? List.of()
        : Arrays.stream(cookies)
            .filter(cookie -> ACCESS_TOKEN_COOKIE.equals(cookie.getName()))
            .toList();
  }

  private OAuth2AuthenticationException invalidRequest(String message) {
    return new OAuth2AuthenticationException(BearerTokenErrors.invalidRequest(message));
  }
}
