package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.InvalidActionStatusException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.model.dto.UpdateActionStatusRequest;
import java.time.LocalDate;
import java.util.Set;

final class ActionRequestValidator {
  private static final String PROMOTION = "PROMOTION";
  private static final String APPROVED = "APPROVED";
  private static final String REJECTED = "REJECTED";
  private static final Set<String> VALID_STATUSES =
      Set.of("GENERATED", "IN_EMPLOYEE_TRIAGE", "SENT_TO_MANAGER", APPROVED, REJECTED);
  private static final Set<String> VALID_ACTION_TYPES = Set.of("ORDER", PROMOTION);

  private ActionRequestValidator() {}

  static void validateFilters(String status, String actionType) {
    if (status != null && !VALID_STATUSES.contains(status)) {
      throw new InvalidRequestException();
    }
    if (actionType != null && !VALID_ACTION_TYPES.contains(actionType)) {
      throw new InvalidRequestException();
    }
  }

  static void validateStatus(UpdateActionStatusRequest request) {
    String status = request.status();
    if (!VALID_STATUSES.contains(status)) {
      throw new InvalidActionStatusException(status);
    }
    if (REJECTED.equals(status)
        && (request.justification() == null || request.justification().isBlank())) {
      throw new InvalidRequestException();
    }
  }

  static void validatePromotionDates(
      String actionType, LocalDate validFrom, LocalDate validUntil, boolean required) {
    if (!PROMOTION.equals(actionType)) {
      rejectDatesForNonPromotion(validFrom, validUntil);
      return;
    }
    validateRequiredPromotionDates(validFrom, validUntil, required);
    validatePromotionDatePair(validFrom, validUntil);
  }

  static void validateFinalDates(
      String actionType, String status, UpdateActionStatusRequest request) {
    boolean hasFinalDates =
        request.finalPromotionValidFrom() != null || request.finalPromotionValidUntil() != null;
    if (hasFinalDates && (!APPROVED.equals(status) || !PROMOTION.equals(actionType))) {
      throw new InvalidRequestException();
    }
    if (hasFinalDates) {
      validatePromotionDates(
          actionType, request.finalPromotionValidFrom(), request.finalPromotionValidUntil(), true);
    }
  }

  private static void rejectDatesForNonPromotion(LocalDate validFrom, LocalDate validUntil) {
    if (validFrom != null || validUntil != null) {
      throw new InvalidRequestException();
    }
  }

  private static void validateRequiredPromotionDates(
      LocalDate validFrom, LocalDate validUntil, boolean required) {
    if (required && (validFrom == null || validUntil == null)) {
      throw new InvalidRequestException();
    }
  }

  private static void validatePromotionDatePair(LocalDate validFrom, LocalDate validUntil) {
    if ((validFrom == null) != (validUntil == null)
        || (validFrom != null && validUntil.isBefore(validFrom))) {
      throw new InvalidRequestException();
    }
  }
}
