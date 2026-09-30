package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.ProductDTO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;

  public ProductRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public List<ProductDTO> findAll(String branch, String category, Boolean status) {
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("branch", branch)
            .addValue("category", category)
            .addValue("status", status)
            .addValue("productErpId", null)
            .addValue("storeErpId", null);
    return jdbc.query(PRODUCT_QUERY, parameters, productRowMapper());
  }

  public Optional<ProductDTO> findByPublicId(String publicId) {
    ProductKey key = parsePublicId(publicId);
    if (key == null) {
      return Optional.empty();
    }
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("branch", null)
            .addValue("category", null)
            .addValue("status", null)
            .addValue("productErpId", key.productErpId())
            .addValue("storeErpId", key.storeErpId());
    return jdbc.query(PRODUCT_QUERY, parameters, productRowMapper()).stream().findFirst();
  }

  public boolean hasSnapshot() {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM product_store WHERE active = TRUE",
            new MapSqlParameterSource(),
            Integer.class);
    return count != null && count > 0;
  }

  private RowMapper<ProductDTO> productRowMapper() {
    return (resultSet, rowNumber) -> toProduct(resultSet);
  }

  private ProductDTO toProduct(ResultSet resultSet) throws SQLException {
    String productErpId = resultSet.getString("product_erp_id");
    String storeErpId = resultSet.getString("store_erp_id");
    LocalDate nearestExpiration = resultSet.getObject("nearest_expiration_date", LocalDate.class);
    Integer expirationDays =
        nearestExpiration == null
            ? null
            : Math.toIntExact(ChronoUnit.DAYS.between(LocalDate.now(clock), nearestExpiration));

    return new ProductDTO(
        productErpId + ":" + storeErpId,
        resultSet.getString("sku"),
        resultSet.getString("product_name"),
        resultSet.getString("category_name"),
        preserveWholeQuantity(resultSet.getBigDecimal("current_stock")),
        preserveWholeQuantity(resultSet.getBigDecimal("minimum_stock_quantity")),
        preserveWholeQuantity(resultSet.getBigDecimal("sales_7d")),
        preserveWholeQuantity(resultSet.getBigDecimal("sales_30d")),
        expirationDays,
        resultSet.getObject("supplier_lead_time_days", Integer.class),
        resultSet.getBigDecimal("sale_price"),
        resultSet.getBigDecimal("unit_cost"),
        resultSet.getTimestamp("last_restock_at") == null
            ? null
            : resultSet.getTimestamp("last_restock_at").toInstant(),
        resultSet.getBigDecimal("current_stock").signum() > 0,
        resultSet.getString("store_name"));
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
