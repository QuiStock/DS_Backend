package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.ProductDTO;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {
  private static final String PRODUCT_QUERY =
      """
      SELECT p.erp_id AS product_erp_id,
             p.sku,
             p.name AS product_name,
             c.name AS category_name,
             s.erp_id AS store_erp_id,
             s.name AS store_name,
             COALESCE(SUM(b.current_balance), 0) AS current_stock,
             ps.minimum_stock_quantity,
             ps.sales_7d,
             ps.sales_30d,
             MIN(b.expiration_date) FILTER (
               WHERE b.active = TRUE AND b.current_balance > 0 AND b.expiration_date IS NOT NULL
             ) AS nearest_expiration_date,
             ps.supplier_lead_time_days,
             ps.sale_price,
             ps.unit_cost,
             ps.last_restock_at
      FROM product_store ps
      JOIN product p ON p.id = ps.product_id
      JOIN store s ON s.id = ps.store_id
      LEFT JOIN category c ON c.id = p.category_id
      LEFT JOIN batch b
        ON b.product_id = p.id AND b.store_id = s.id AND b.active = TRUE
      WHERE ps.active = TRUE
        AND p.active = TRUE
        AND s.status = 'ACTIVE'
        /*STORE_SCOPE*/
        AND (CAST(:productErpId AS text) IS NULL OR p.erp_id = :productErpId)
        AND (CAST(:storeErpId AS text) IS NULL OR s.erp_id = :storeErpId)
        AND (CAST(:branch AS text) IS NULL OR s.name = :branch OR s.erp_id = :branch)
        AND (CAST(:category AS text) IS NULL OR c.name = :category)
      GROUP BY p.id, p.erp_id, p.sku, p.name, c.name,
               s.id, s.erp_id, s.name, ps.id, ps.minimum_stock_quantity,
               ps.sales_7d, ps.sales_30d, ps.supplier_lead_time_days,
               ps.sale_price, ps.unit_cost, ps.last_restock_at
      HAVING CAST(:status AS boolean) IS NULL
         OR (COALESCE(SUM(b.current_balance), 0) > 0) = CAST(:status AS boolean)
      ORDER BY p.name, s.name
      """;

  private final EntityManager entityManager;
  private final Clock clock;

  public ProductRepository(EntityManager entityManager, Clock clock) {
    this.entityManager = entityManager;
    this.clock = clock;
  }

  public List<ProductDTO> findAll(
      String branch, String category, Boolean status, StoreScope scope) {
    Map<String, Object> parameters = new HashMap<>(scope.parameters());
    parameters.put("branch", branch);
    parameters.put("category", category);
    parameters.put("status", status);
    parameters.put("productErpId", null);
    parameters.put("storeErpId", null);
    return findProducts(parameters, scope);
  }

  public Optional<ProductDTO> findByPublicId(String publicId, StoreScope scope) {
    ProductKey key = parsePublicId(publicId);
    if (key == null) {
      return Optional.empty();
    }
    Map<String, Object> parameters = new HashMap<>(scope.parameters());
    parameters.put("branch", null);
    parameters.put("category", null);
    parameters.put("status", null);
    parameters.put("productErpId", key.productErpId());
    parameters.put("storeErpId", key.storeErpId());
    return findProducts(parameters, scope).stream().findFirst();
  }

  public boolean hasSnapshot() {
    Map<String, Object> row =
        JpaNativeQuery.rows(
                entityManager,
                "SELECT COUNT(*) AS count FROM product_store WHERE active = TRUE",
                Map.of())
            .getFirst();
    return JpaNativeQuery.longValue(row, "count") > 0;
  }

  private List<ProductDTO> findProducts(Map<String, Object> parameters, StoreScope scope) {
    String query = PRODUCT_QUERY.replace("/*STORE_SCOPE*/", scope.sql());
    return JpaNativeQuery.rows(entityManager, query, parameters).stream()
        .map(this::toProduct)
        .toList();
  }

  private ProductDTO toProduct(Map<String, Object> row) {
    String productErpId = JpaNativeQuery.string(row, "product_erp_id");
    String storeErpId = JpaNativeQuery.string(row, "store_erp_id");
    LocalDate nearestExpiration = JpaNativeQuery.date(row, "nearest_expiration_date");
    Integer expirationDays =
        nearestExpiration == null
            ? null
            : Math.toIntExact(ChronoUnit.DAYS.between(LocalDate.now(clock), nearestExpiration));

    return new ProductDTO(
        productErpId + ":" + storeErpId,
        JpaNativeQuery.string(row, "sku"),
        JpaNativeQuery.string(row, "product_name"),
        JpaNativeQuery.string(row, "category_name"),
        preserveWholeQuantity(JpaNativeQuery.decimal(row, "current_stock")),
        preserveWholeQuantity(JpaNativeQuery.decimal(row, "minimum_stock_quantity")),
        preserveWholeQuantity(JpaNativeQuery.decimal(row, "sales_7d")),
        preserveWholeQuantity(JpaNativeQuery.decimal(row, "sales_30d")),
        expirationDays,
        JpaNativeQuery.intValue(row, "supplier_lead_time_days"),
        JpaNativeQuery.decimal(row, "sale_price"),
        JpaNativeQuery.decimal(row, "unit_cost"),
        JpaNativeQuery.instant(row, "last_restock_at"),
        JpaNativeQuery.decimal(row, "current_stock").signum() > 0,
        JpaNativeQuery.string(row, "store_name"));
  }

  private ProductKey parsePublicId(String publicId) {
    if (publicId == null) {
      return null;
    }
    int separator = publicId.indexOf(':');
    if (separator <= 0 || separator == publicId.length() - 1) {
      return null;
    }
    return new ProductKey(publicId.substring(0, separator), publicId.substring(separator + 1));
  }

  private Number preserveWholeQuantity(java.math.BigDecimal value) {
    if (value == null) {
      return null;
    }
    try {
      return value.intValueExact();
    } catch (ArithmeticException exception) {
      return value;
    }
  }

  private record ProductKey(String productErpId, String storeErpId) {}
}
