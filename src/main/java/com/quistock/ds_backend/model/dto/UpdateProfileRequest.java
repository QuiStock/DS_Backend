package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.quistock.ds_backend.exception.InvalidRequestException;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 150) String name,
    @JsonProperty("profile_photo_url") @Size(max = 500) String profilePhotoUrl) {
  @JsonAnySetter
  public void rejectUnknownField(String field, Object value) {
    throw new InvalidRequestException();
  }
}
