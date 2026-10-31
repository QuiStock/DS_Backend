package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.RegionDTO;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RegionRepository {
  private static final String NAME_COLUMN = "name";

  private final EntityManager entityManager;

  public RegionRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public List<RegionDTO> findActiveRegions() {
    return JpaNativeQuery.rows(
            entityManager,
            "SELECT id, code, name FROM region_type WHERE active = TRUE ORDER BY name, id",
            java.util.Map.of())
        .stream()
        .map(
            row ->
                new RegionDTO(
                    Long.toString(JpaNativeQuery.longValue(row, "id")),
                    JpaNativeQuery.string(row, "code"),
                    JpaNativeQuery.string(row, NAME_COLUMN)))
        .toList();
  }
}
