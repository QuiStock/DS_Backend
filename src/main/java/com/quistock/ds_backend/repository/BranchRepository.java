package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.BranchDTO;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class BranchRepository {
  private final EntityManager entityManager;

  public BranchRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public boolean isActiveInScope(long storeId, StoreScope scope) {
    Map<String, Object> parameters = scope.parameters();
    parameters.put("storeId", storeId);
    return Boolean.TRUE.equals(
        JpaNativeQuery.rows(
                entityManager,
                "SELECT EXISTS (SELECT 1 FROM store s WHERE s.id = :storeId "
                    + scope.sql()
                    + ") AS allowed",
                parameters)
            .getFirst()
            .get("allowed"));
  }

  public List<BranchDTO> findActiveBranches(StoreScope scope) {
    return JpaNativeQuery.rows(
            entityManager,
            """
        SELECT s.id AS store_id, s.erp_id, s.name, s.region_id,
               a.street, a.number, a.complement, a.neighborhood, a.city, a.state, a.postal_code
        FROM store s
        LEFT JOIN store_address a ON a.store_id = s.id
        WHERE TRUE
        """
                + scope.sql()
                + " ORDER BY s.name",
            scope.parameters())
        .stream()
        .map(
            row -> {
              String street = JpaNativeQuery.string(row, "street");
              String number = JpaNativeQuery.string(row, "number");
              String complement = JpaNativeQuery.string(row, "complement");
              String address = joinAddress(street, number, complement);
              return new BranchDTO(
                  JpaNativeQuery.string(row, "erp_id"),
                  JpaNativeQuery.longValue(row, "store_id"),
                  JpaNativeQuery.string(row, "name"),
                  address,
                  JpaNativeQuery.string(row, "city"),
                  JpaNativeQuery.string(row, "state"),
                  null,
                  null,
                  JpaNativeQuery.longValue(row, "region_id"));
            })
        .toList();
  }

  private String joinAddress(String street, String number, String complement) {
    String address =
        java.util.stream.Stream.of(street, number, complement)
            .filter(value -> value != null && !value.isBlank())
            .collect(java.util.stream.Collectors.joining(", "));
    return address.isEmpty() ? null : address;
  }
}
