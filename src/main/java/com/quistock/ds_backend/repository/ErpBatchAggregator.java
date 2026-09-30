package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.ErpBatchDTO;
import com.quistock.ds_backend.util.ErpValueParser;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

final class ErpBatchAggregator {
  private ErpBatchAggregator() {}

  static BigDecimal sum(List<ErpBatchDTO> batches, Function<ErpBatchDTO, Object> field) {
    return batches.stream()
        .map(field)
        .map(ErpValueParser::toBigDecimal)
        .filter(value -> value != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static BigDecimal maximumDecimal(List<ErpBatchDTO> batches, Function<ErpBatchDTO, Object> field) {
    return batches.stream()
        .map(field)
        .map(ErpValueParser::toBigDecimal)
        .filter(value -> value != null)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }

  static Integer maximumInteger(List<ErpBatchDTO> batches, Function<ErpBatchDTO, Object> field) {
    return batches.stream()
        .map(field)
        .map(ErpValueParser::toInteger)
        .filter(value -> value != null)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }

  static ErpBatchDTO mostRecent(List<ErpBatchDTO> batches) {
    return batches.stream()
        .max(
            Comparator.comparing(
                    (ErpBatchDTO batch) -> instant(batch.entryDate()),
                    Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(batch -> java.util.Objects.toString(batch.id(), "")))
        .orElseThrow();
  }

  static BigDecimal decimal(Object value) {
    return ErpValueParser.toBigDecimal(value);
  }

  static java.time.Instant instant(Object value) {
    return ErpValueParser.toInstant(value);
  }
}
