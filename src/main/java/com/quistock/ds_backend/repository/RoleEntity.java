package com.quistock.ds_backend.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "role")
public class RoleEntity {
  @Id private Long id;

  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false, unique = true)
  private String name;

  protected RoleEntity() {}

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }
}
