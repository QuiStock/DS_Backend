package com.quistock.ds_backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.query.NativeQuery;

/** Small helpers for native PostgreSQL queries that cannot be expressed faithfully in JPQL. */
final class JpaNativeQuery {
  private JpaNativeQuery() {}

  static List<Map<String, Object>> rows(
      EntityManager entityManager, String sql, Map<String, ?> parameters) {
    NativeQuery<?> query = entityManager.createNativeQuery(sql).unwrap(NativeQuery.class);
    bind(query, parameters);
    query.setTupleTransformer(
        (tuple, aliases) -> {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int index = 0; index < aliases.length; index++) {
            row.put(aliases[index].toLowerCase(java.util.Locale.ROOT), tuple[index]);
          }
          return row;
        });
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result = (List<Map<String, Object>>) query.getResultList();
    return result;
  }

  static int update(EntityManager entityManager, String sql, Map<String, ?> parameters) {
    Query query = entityManager.createNativeQuery(sql);
    bind(query, parameters);
    return query.executeUpdate();
  }

  static void bind(Query query, Map<String, ?> parameters) {
    parameters.forEach(query::setParameter);
  }

  static Map<String, Object> parameters(Object... keyValues) {
    if (keyValues.length % 2 != 0) {
      throw new IllegalArgumentException("Native query parameters must be key/value pairs.");
    }
    Map<String, Object> parameters = new LinkedHashMap<>();
    for (int index = 0; index < keyValues.length; index += 2) {
      parameters.put((String) keyValues[index], keyValues[index + 1]);
    }
    return parameters;
  }

  static String string(Map<String, Object> row, String column) {
    Object value = row.get(column);
    return value == null ? null : value.toString();
  }

  static Long longValue(Map<String, Object> row, String column) {
    Object value = row.get(column);
    return value == null ? null : ((Number) value).longValue();
  }

  static Integer intValue(Map<String, Object> row, String column) {
    Object value = row.get(column);
    return value == null ? null : ((Number) value).intValue();
  }

  static BigDecimal decimal(Map<String, Object> row, String column) {
    Object value = row.get(column);
    return value == null
        ? null
        : value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
  }

  static LocalDate date(Map<String, Object> row, String column) {
    Object value = row.get(column);
    if (value instanceof LocalDate localDate) {
      return localDate;
    }
    if (value instanceof java.sql.Date date) {
      return date.toLocalDate();
    }
    return value == null ? null : LocalDate.parse(value.toString());
  }

  static Instant instant(Map<String, Object> row, String column) {
    Object value = row.get(column);
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof Timestamp timestamp) {
      return timestamp.toInstant();
    }
    if (value instanceof java.time.OffsetDateTime offsetDateTime) {
      return offsetDateTime.toInstant();
    }
    return value == null ? null : Instant.parse(value.toString());
  }
}
