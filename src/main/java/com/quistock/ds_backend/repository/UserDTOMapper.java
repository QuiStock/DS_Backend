package com.quistock.ds_backend.repository;

import com.quistock.ds_backend.model.dto.UserDTO;
import java.util.Map;

final class UserDTOMapper {
  private UserDTOMapper() {}

  static UserDTO map(Map<String, Object> row) {
    return new UserDTO(
        Long.toString(JpaNativeQuery.longValue(row, "id")),
        JpaNativeQuery.string(row, "name"),
        JpaNativeQuery.string(row, "email"),
        JpaNativeQuery.string(row, "role"),
        JpaNativeQuery.string(row, "status"),
        JpaNativeQuery.string(row, "profile_photo_url"),
        JpaNativeQuery.longValue(row, "store_id"),
        JpaNativeQuery.string(row, "store_code"),
        JpaNativeQuery.string(row, "store_name"),
        JpaNativeQuery.longValue(row, "region_id"),
        JpaNativeQuery.string(row, "region_code"),
        JpaNativeQuery.string(row, "region_name"));
  }
}
