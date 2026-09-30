package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.FlowDTO;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class FlowRepository {
  private static final String FLOW_SELECT =
      """
      SELECT pa.id,
             p.erp_id || ':' || s.erp_id AS public_product_id,
             p.name AS product_name,
             ft.code::text AS flow_type,
             COALESCE(pa.context ->> 'reason', '') AS reason,
             (pa.metrics ->> 'daily_sales_average')::numeric AS daily_sales_average,
             (pa.metrics ->> 'stock_coverage_days')::numeric AS stock_coverage_days,
             NULLIF(pa.metrics ->> 'expiration_days', 'null')::integer AS expiration_days,
             NULLIF(pa.metrics ->> 'supplier_lead_time', 'null')::integer AS supplier_lead_time,
             pa.created_at AS analysis_date
      FROM product_analysis pa
      JOIN product p ON p.id = pa.product_id
      JOIN store s ON s.id = pa.store_id
      JOIN flow_type_catalog ft ON ft.id = pa.flow_type_id
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public FlowRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public long insertAnalysis(AnalysisSnapshot analysis) {
    ProductKey key = parseProductId(analysis.publicProductId());
    if (key == null) {
      return -1;
    }

    Map<String, Object> metrics = new HashMap<>();
    metrics.put("daily_sales_average", analysis.dailySalesAverage());
    metrics.put("stock_coverage_days", analysis.stockCoverageDays());
    metrics.put("current_stock", analysis.currentStock());
    metrics.put("minimum_stock", analysis.minimumStock());
    metrics.put("sales_7d", analysis.sales7d());
    metrics.put("sales_30d", analysis.sales30d());
    metrics.put("expiration_days", analysis.expirationDays());
    metrics.put("supplier_lead_time", analysis.supplierLeadTime());
    Map<String, Object> context = Map.of("reason", analysis.reason(), "classifier", "RULE_ENGINE");

    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("productErpId", key.productErpId())
            .addValue("storeErpId", key.storeErpId())
            .addValue("flowType", analysis.flowType())
            .addValue("metrics", toJson(metrics))
            .addValue("context", toJson(context))
            .addValue("createdAt", OffsetDateTime.ofInstant(analysis.createdAt(), ZoneOffset.UTC));

    List<Long> ids =
        jdbc.query(
            """
            INSERT INTO product_analysis (
              ml_execution_id, store_id, product_id, flow_type_id, metrics, context, created_at
            )
            SELECT NULL, ps.store_id, ps.product_id, ft.id,
                   CAST(:metrics AS jsonb), CAST(:context AS jsonb), :createdAt
            FROM product_store ps
            JOIN product p ON p.id = ps.product_id
            JOIN store s ON s.id = ps.store_id
            JOIN flow_type_catalog ft ON ft.code = CAST(:flowType AS flow_type)
            WHERE p.erp_id = :productErpId
              AND s.erp_id = :storeErpId
              AND ps.active = TRUE
              AND p.active = TRUE
              AND s.status = 'ACTIVE'
            RETURNING id
            """,
            parameters,
            (resultSet, rowNumber) -> resultSet.getLong("id"));
    return ids.isEmpty() ? -1 : ids.get(0);
  }

  public List<FlowDTO> findAll(String flowType, String productId, String status) {
    ProductKey key = productId == null ? null : parseProductId(productId);
    if (productId != null && key == null) {
      return List.of();
    }
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("flowType", flowType)
            .addValue("productErpId", key == null ? null : key.productErpId())
            .addValue("storeErpId", key == null ? null : key.storeErpId());

    String sql =
        FLOW_SELECT
            + " WHERE (CAST(:flowType AS text) IS NULL OR ft.code = CAST(:flowType AS flow_type))"
            + " AND (CAST(:productErpId AS text) IS NULL OR (p.erp_id = :productErpId AND s.erp_id = :storeErpId))"
            + " ORDER BY pa.created_at DESC, pa.id DESC";
    if (status != null && !"ANALYZED".equals(status)) {
      return List.of();
    }
    return jdbc.query(sql, parameters, (resultSet, rowNumber) -> toFlow(resultSet));
  }

  public Optional<FlowDTO> findById(String id) {
    Long analysisId = parseId(id);
    if (analysisId == null) {
      return Optional.empty();
    }
    List<FlowDTO> flows =
        jdbc.query(
            FLOW_SELECT + " WHERE pa.id = :id",
            new MapSqlParameterSource("id", analysisId),
            (resultSet, rowNumber) -> toFlow(resultSet));
    return flows.stream().findFirst();
  }

  private FlowDTO toFlow(java.sql.ResultSet resultSet) throws java.sql.SQLException {
    OffsetDateTime analysisDate = resultSet.getObject("analysis_date", OffsetDateTime.class);
    return new FlowDTO(
        resultSet.getString("id"),
        resultSet.getString("public_product_id"),
        resultSet.getString("product_name"),
        resultSet.getString("flow_type"),
        "ANALYZED",
        resultSet.getString("reason"),
        resultSet.getBigDecimal("daily_sales_average"),
        resultSet.getBigDecimal("stock_coverage_days"),
        resultSet.getObject("expiration_days", Integer.class),
        resultSet.getObject("supplier_lead_time", Integer.class),
        analysisDate == null ? null : analysisDate.toInstant());
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Could not serialize analysis JSON.", exception);
    }
  }

  private ProductKey parseProductId(String publicProductId) {
    if (publicProductId == null) {
      return null;
    }
    int separator = publicProductId.indexOf(':');
    if (separator <= 0 || separator == publicProductId.length() - 1) {
      return null;
    }
    return new ProductKey(
        publicProductId.substring(0, separator), publicProductId.substring(separator + 1));
  }

  private Long parseId(String id) {
    try {
      return id == null ? null : Long.parseLong(id);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private record ProductKey(String productErpId, String storeErpId) {}

  public record AnalysisSnapshot(
      String publicProductId,
      String flowType,
      BigDecimal dailySalesAverage,
      BigDecimal stockCoverageDays,
      BigDecimal currentStock,
      BigDecimal minimumStock,
      BigDecimal sales7d,
      BigDecimal sales30d,
      Integer expirationDays,
      Integer supplierLeadTime,
      String reason,
      Instant createdAt) {}
}
