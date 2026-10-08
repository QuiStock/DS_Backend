package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.RegionDTO;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RegionRepository {
  private static final String NAME_COLUMN = "name";

  private final NamedParameterJdbcTemplate jdbc;

  public RegionRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<RegionDTO> findActiveRegions() {
    return jdbc.query(
        "SELECT id, code, name FROM region_type WHERE active = TRUE ORDER BY name, id",
        Map.of(),
        (rs, row) ->
            new RegionDTO(
                Long.toString(rs.getLong("id")), rs.getString("code"), rs.getString(NAME_COLUMN)));
  }
}
