package com.quistock.ds_backend.config;

import java.net.URI;

final class DeploymentSettingsValidator {
  private DeploymentSettingsValidator() {}

  static void requireHttpUrl(String name, String value) {
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(name + " must be a complete HTTP(S) URL.", exception);
    }
    if (uri.getHost() == null
        || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
        || uri.getUserInfo() != null
        || uri.getFragment() != null) {
      throw new IllegalStateException(name + " must be a complete HTTP(S) URL.");
    }
  }

  static void requireAudience(String audience) {
    if (audience.isBlank()) {
      throw new IllegalStateException("AUTH_JWT_AUDIENCE must not be blank.");
    }
  }
}
