package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.FlowDTO;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class FlowRepository {
  private static final String FLOW_SELECT =
      """
      SELECT pa.id, pa.store_id,
             COALESCE(pa.classifier, 'RULE_ENGINE') AS classifier,
             COALESCE(pa.analysis_status, 'ANALYZED') AS analysis_status,
             COALESCE(pa.context -> 'risks', '[]'::jsonb)::text AS risks,
             (pa.classifier = 'SALES_PERCENTILES_V1' AND p.active = TRUE
               AND EXISTS (SELECT 1 FROM product_store current_ps
                 WHERE current_ps.product_id = pa.product_id AND current_ps.store_id = pa.store_id
                   AND current_ps.active = TRUE)
               AND NOT EXISTS (SELECT 1 FROM product_analysis newer
                 WHERE newer.store_id = pa.store_id AND newer.product_id = pa.product_id
                   AND (newer.created_at, newer.id) > (pa.created_at, pa.id))) AS is_current,
             p.erp_id || ':' || s.erp_id AS public_product_id,
             p.name AS product_name,
             ft.code::text AS flow_type,
             COALESCE(pa.reason, '') AS reason,
             pa.daily_sales_average,
             pa.stock_coverage_days,
             pa.expiration_days,
             COALESCE(
               pa.supplier_lead_time_days,
               NULLIF(pa.context #>> '{legacy_metrics,supplier_lead_time}', 'null')::integer
             ) AS supplier_lead_time,
             pa.created_at AS analysis_date
      FROM product_analysis pa
      JOIN product p ON p.id = pa.product_id
      JOIN store s ON s.id = pa.store_id
      LEFT JOIN flow_type_catalog ft ON ft.id = pa.flow_type_id
      """;

  private final EntityManager entityManager;
  private final ObjectMapper objectMapper;

  public FlowRepository(EntityManager entityManager, ObjectMapper objectMapper) {
    this.entityManager = entityManager;
    this.objectMapper = objectMapper;
  }

  public long insertAnalysis(AnalysisSnapshot analysis, StoreScope scope, long executionId) {
    ProductKey key = parseProductId(analysis.publicProductId());
    if (key == null) {
      return -1;
    }

    Map<String, Object> parameters = new HashMap<>(scope.parameters());
    parameters.put("productErpId", key.productErpId());
    parameters.put("storeErpId", key.storeErpId());
    JpaNativeQuery.rows(
        entityManager,
        """
        SELECT ps.id FROM product_store ps JOIN product p ON p.id = ps.product_id
        JOIN store s ON s.id = ps.store_id
        WHERE p.erp_id = :productErpId AND s.erp_id = :storeErpId
        """
            + scope.sql()
            + " FOR UPDATE OF ps",
        parameters);
    Map<String, Object> context = new HashMap<>();
    context.put("risks", analysis.risks());

    parameters.put("executionId", executionId);
    parameters.put("flowType", analysis.flowType());
    parameters.put("classifier", "SALES_PERCENTILES_V1");
    parameters.put(
        "analysisStatus", analysis.flowType() == null ? "INSUFFICIENT_DATA" : "ANALYZED");
    parameters.put("reason", analysis.reason());
    parameters.put("dailySalesAverage", analysis.dailySalesAverage());
    parameters.put("stockCoverageDays", analysis.stockCoverageDays());
    parameters.put("currentStock", analysis.currentStock());
    parameters.put("minimumStock", analysis.minimumStock());
    parameters.put("sales7d", analysis.sales7d());
    parameters.put("sales30d", analysis.sales30d());
    parameters.put("expirationDays", analysis.expirationDays());
    parameters.put("supplierLeadTimeDays", analysis.supplierLeadTime());
    parameters.put("context", toJson(context));
    parameters.put("createdAt", OffsetDateTime.ofInstant(analysis.createdAt(), ZoneOffset.UTC));

    List<Map<String, Object>> ids =
        JpaNativeQuery.rows(
            entityManager,
            """
            INSERT INTO product_analysis (
              ml_execution_id, store_id, product_id, flow_type_id, classifier, analysis_status,
              reason, daily_sales_average, stock_coverage_days, current_stock, minimum_stock,
              sales_7d, sales_30d, expiration_days, supplier_lead_time_days, context, created_at
            )
            SELECT :executionId, ps.store_id, ps.product_id, ft.id,
                   CAST(:classifier AS varchar(100)),
                   CAST(:analysisStatus AS varchar(40)),
                   CAST(:reason AS text),
                   CAST(:dailySalesAverage AS numeric(14,6)),
                   CAST(:stockCoverageDays AS numeric(14,6)),
                   CAST(:currentStock AS numeric(14,3)),
                   CAST(:minimumStock AS numeric(14,3)),
                   CAST(:sales7d AS numeric(14,3)),
                   CAST(:sales30d AS numeric(14,3)),
                   CAST(:expirationDays AS integer),
                   CAST(:supplierLeadTimeDays AS integer),
                   CAST(:context AS jsonb), :createdAt
            FROM product_store ps
            JOIN product p ON p.id = ps.product_id
            JOIN store s ON s.id = ps.store_id
            LEFT JOIN flow_type_catalog ft ON ft.code = CAST(:flowType AS flow_type)
            WHERE p.erp_id = :productErpId
              AND s.erp_id = :storeErpId
              AND ps.active = TRUE
              AND p.active = TRUE
            """
                + scope.sql()
                + " RETURNING id",
            parameters);
    return ids.isEmpty() ? -1 : JpaNativeQuery.longValue(ids.get(0), "id");
  }

  public List<FlowDTO> findAll(String flowType, String productId, String status, StoreScope scope) {
    ProductKey key = productId == null ? null : parseProductId(productId);
    if (productId != null && key == null) {
      return List.of();
    }
    Map<String, Object> parameters = new HashMap<>(scope.parameters());
    parameters.put("flowType", flowType);
    parameters.put("productErpId", key == null ? null : key.productErpId());
    parameters.put("storeErpId", key == null ? null : key.storeErpId());

    String sql =
        FLOW_SELECT
            + " WHERE (CAST(:flowType AS text) IS NULL OR ft.code = CAST(:flowType AS flow_type))"
            + " AND (CAST(:productErpId AS text) IS NULL OR (p.erp_id = :productErpId AND s.erp_id"
            + " = :storeErpId))"
            + scope.sql()
            + " ORDER BY pa.created_at DESC, pa.id DESC";
    if (status != null) {
      sql =
          sql.replace(
              " ORDER BY",
              " AND COALESCE(pa.analysis_status, 'ANALYZED') = :analysisStatus ORDER BY");
      parameters.put("analysisStatus", status);
    }
    return JpaNativeQuery.rows(entityManager, sql, parameters).stream().map(this::toFlow).toList();
  }

  public Optional<FlowDTO> findById(String id, StoreScope scope) {
    Long analysisId = parseId(id);
    if (analysisId == null) {
      return Optional.empty();
    }
    Map<String, Object> parameters = new HashMap<>(scope.parameters());
    parameters.put("id", analysisId);
    List<FlowDTO> flows =
        JpaNativeQuery.rows(
                entityManager, FLOW_SELECT + " WHERE pa.id = :id" + scope.sql(), parameters)
            .stream()
            .map(this::toFlow)
            .toList();
    return flows.stream().findFirst();
  }

  private FlowDTO toFlow(Map<String, Object> row) {
    return new FlowDTO(
        Objects.toString(row.get("id")),
        JpaNativeQuery.string(row, "public_product_id"),
        JpaNativeQuery.string(row, "product_name"),
        JpaNativeQuery.string(row, "flow_type"),
        JpaNativeQuery.string(row, "analysis_status"),
        JpaNativeQuery.string(row, "reason"),
        JpaNativeQuery.decimal(row, "daily_sales_average"),
        JpaNativeQuery.decimal(row, "stock_coverage_days"),
        JpaNativeQuery.intValue(row, "expiration_days"),
        JpaNativeQuery.intValue(row, "supplier_lead_time"),
        JpaNativeQuery.instant(row, "analysis_date"),
        readRisks(JpaNativeQuery.string(row, "risks")),
        JpaNativeQuery.string(row, "classifier"),
        Boolean.TRUE.equals(row.get("is_current")),
        JpaNativeQuery.longValue(row, "store_id"));
  }

  public List<BigDecimal> comparableSales(String publicId, StoreScope scope) {
    Map<String, Object> parameters = new HashMap<>(scope.parameters());
    parameters.put("publicId", publicId);
    return JpaNativeQuery.rows(
            entityManager,
            """
        SELECT peer.sales_7d FROM product_store target
        JOIN product p ON p.id = target.product_id
        JOIN store s ON s.id = target.store_id
        JOIN product other ON other.category_id = p.category_id
          AND other.active = TRUE
        JOIN product_store peer ON peer.product_id = other.id AND peer.store_id = s.id
        WHERE p.erp_id || ':' || s.erp_id = :publicId
          AND p.active = TRUE AND target.active = TRUE AND peer.active = TRUE
          AND peer.sales_7d IS NOT NULL
        """
                + scope.sql(),
            parameters)
        .stream()
        .map(row -> JpaNativeQuery.decimal(row, "sales_7d"))
        .toList();
  }

  private List<String> readRisks(String json) {
    try {
      return objectMapper.readValue(
          json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    } catch (JacksonException exception) {
      throw new IllegalStateException("Could not read analysis risks.", exception);
    }
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
      Instant createdAt,
      List<String> risks) {}
}
