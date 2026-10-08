package com.quistock.ds_backend.repository;

public record NewUserAccount(
    String roleCode, String name, String email, String passwordHash, long createdById) {}
