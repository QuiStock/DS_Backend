package com.quistock.ds_backend.util;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public final class ErpValueParser {

  private static final long EPOCH_MILLIS_THRESHOLD = 100_000_000_000L;

  private ErpValueParser() {}

  public static Integer toInteger(Object value) {
    if (value == null) {
      return null;
    }

    try {
      return new BigDecimal(value.toString().trim()).intValueExact();
    } catch (NumberFormatException | ArithmeticException exception) {
      throw new IllegalArgumentException("Valor inteiro inválido recebido do ERP.", exception);
    }
  }

  public static int toIntegerOrZero(Object value) {
    Integer parsedValue = toInteger(value);
    return parsedValue == null ? 0 : parsedValue;
  }

  public static BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return null;
    }

    try {
      return new BigDecimal(value.toString().trim());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Valor decimal inválido recebido do ERP.", exception);
    }
  }

  public static LocalDate toLocalDate(Object value) {
    Instant instant = toInstant(value);
    return instant == null ? null : instant.atZone(ZoneOffset.UTC).toLocalDate();
  }

  public static Instant toInstant(Object value) {
    if (value == null) {
      return null;
    }

    String text = value.toString().trim();
    if (text.isEmpty()) {
      return null;
    }

    try {
      if (text.matches("[+-]?\\d+")) {
        return fromUnixTimestamp(Long.parseLong(text));
      }

      return Instant.parse(text);
    } catch (DateTimeParseException | NumberFormatException exception) {
      try {
        return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
      } catch (DateTimeParseException dateException) {
        throw new IllegalArgumentException("Data inválida recebida do ERP.", dateException);
      }
    }
  }

  private static Instant fromUnixTimestamp(long timestamp) {
    if (Math.abs(timestamp) < EPOCH_MILLIS_THRESHOLD) {
      return Instant.ofEpochSecond(timestamp);
    }

    return Instant.ofEpochMilli(timestamp);
  }
}
