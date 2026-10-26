package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.model.dto.GenerateActionsRequest;
import com.quistock.ds_backend.repository.ActionRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class ActionCreationValidator {
  private static final String ORDER = "ORDER";
  private static final String PROMOTION = "PROMOTION";
  private final Clock clock;

  public ActionCreationValidator(Clock clock) {
    this.clock = clock;
  }

  ActionRepository.CreationDetails validate(String type, GenerateActionsRequest request) {
    if (PROMOTION.equals(type)) {
      return promotionDetails(request);
    }
    validateOrder(request);
    return new ActionRepository.CreationDetails(
        type,
        "User-requested stock replenishment.",
        null,
        null,
        request.quantity(),
        request.desiredOrderDate(),
        null);
  }

  private ActionRepository.CreationDetails promotionDetails(GenerateActionsRequest request) {
    requirePromotionFields(request);
    LocalDate from = request.promotionValidFrom();
    LocalDate until = request.promotionValidUntil();
    if (request.promotionDurationDays() != null) {
      if (from != null || until != null) {
        throw new InvalidRequestException();
      }
      from = LocalDate.now(clock);
      until = from.plusDays(request.promotionDurationDays() - 1L);
    }
    ActionRequestValidator.validatePromotionDates(PROMOTION, from, until, true);
    if (from.isBefore(LocalDate.now(clock))) {
      throw new InvalidRequestException();
    }
    return new ActionRepository.CreationDetails(
        PROMOTION,
        "User-requested expiration-risk promotion.",
        from,
        until,
        null,
        null,
        request.discountPercentage());
  }

  private void requirePromotionFields(GenerateActionsRequest request) {
    if (request.quantity() != null
        || request.desiredOrderDate() != null
        || request.discountPercentage() == null) {
      throw new InvalidRequestException();
    }
  }

  private void validateOrder(GenerateActionsRequest request) {
    if (request.quantity() == null
        || request.desiredOrderDate() == null
        || request.discountPercentage() != null
        || request.promotionDurationDays() != null) {
      throw new InvalidRequestException();
    }
    if (request.desiredOrderDate().isBefore(LocalDate.now(clock))) {
      throw new InvalidRequestException();
    }
    ActionRequestValidator.validatePromotionDates(
        ORDER, request.promotionValidFrom(), request.promotionValidUntil(), false);
  }
}
