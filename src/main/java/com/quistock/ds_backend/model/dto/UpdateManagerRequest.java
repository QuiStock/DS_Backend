package com.quistock.ds_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateManagerRequest(
    @Size(max = 150) String name,
    @Email @Size(max = 255) String email,
    String role,
    String status,
    @JsonProperty("replacement_manager_id") Long replacementManagerId) {}
