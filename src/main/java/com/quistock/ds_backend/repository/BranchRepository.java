package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.BranchDTO;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BranchRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public BranchRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<BranchDTO> findActiveBranches() {
    return jdbc.query(
        """
        SELECT s.erp_id, s.name,
               a.street, a.number, a.complement, a.neighborhood, a.city, a.state, a.postal_code
        FROM store s
        LEFT JOIN store_address a ON a.store_id = s.id
        WHERE s.status = 'ACTIVE'
        ORDER BY s.name
        """,
        (resultSet, rowNumber) -> {
          String street = resultSet.getString("street");
          String number = resultSet.getString("number");
          String complement = resultSet.getString("complement");
          String address = joinAddress(street, number, complement);
          return new BranchDTO(
              resultSet.getString("erp_id"),
              resultSet.getString("name"),
              address,
              resultSet.getString("city"),
              resultSet.getString("state"),
              null,
              null);
        });
  }

  private String joinAddress(String street, String number, String complement) {
    String address =
        java.util.stream.Stream.of(street, number, complement)
        .filter(value -> value != null && !value.isBlank())
        .collect(java.util.stream.Collectors.joining(", "));
    return address.isEmpty() ? null : address;
  }
}
