package com.quistock.ds_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

final class CookieCsrfProtectionFilter extends OncePerRequestFilter {
  private static final String ACCESS_TOKEN_COOKIE = "access_token";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";
  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

  private final CsrfTokenRepository tokenRepository;

  CookieCsrfProtectionFilter(CsrfTokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return SAFE_METHODS.contains(request.getMethod())
        || hasBearerAuthorization(request)
        || !hasAccessTokenCookie(request);
  }

  @Override
  @SuppressWarnings("PMD.LawOfDemeter")
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken csrfToken = tokenRepository.loadToken(request);
    String submittedToken = request.getHeader(CSRF_HEADER);
    if (csrfToken == null || !matches(csrfToken.getToken(), submittedToken)) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
      response.setContentType("application/json");
      response
          .getWriter()
          .write("{\"code\":\"CSRF_REQUIRED\",\"message\":\"A valid CSRF token is required.\"}");
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean hasBearerAuthorization(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    return authorization != null
        && authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length());
  }

  private boolean hasAccessTokenCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    return cookies != null
        && Arrays.stream(cookies)
            .anyMatch(
                cookie ->
                    ACCESS_TOKEN_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank());
  }

  private boolean matches(String expectedToken, String submittedToken) {
    return submittedToken != null
        && MessageDigest.isEqual(
            expectedToken.getBytes(StandardCharsets.UTF_8),
            submittedToken.getBytes(StandardCharsets.UTF_8));
  }
}
