package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.InvalidRequestException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
class UserInputNormalizer {
  String email(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      throw new InvalidRequestException();
    }
    return normalized;
  }

  String optional(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.isBlank()) {
      throw new InvalidRequestException();
    }
    return normalized;
  }

  String status(String value) {
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!UserManagementRules.ACTIVE.equals(normalized)
        && !UserManagementRules.INACTIVE.equals(normalized)) {
      throw new InvalidRequestException();
    }
    return normalized;
  }

  String teamRole(String value) {
    if (value == null) {
      throw new InvalidRequestException();
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!UserManagementRules.EMPLOYEE.equals(normalized)
        && !UserManagementRules.REGIONAL_MANAGER.equals(normalized)) {
      throw new InvalidRequestException();
    }
    return normalized;
  }
}
