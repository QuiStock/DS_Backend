package com.quistock.ds_backend.model.dto;

public record BranchDTO(
    String id,
    String name,
    String address,
    String city,
    String state,
    Double latitude,
    Double longitude) {}
