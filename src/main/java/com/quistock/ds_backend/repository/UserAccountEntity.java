package com.quistock.ds_backend.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "user_account")
public class UserAccountEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "role_id", nullable = false)
  private Long roleId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "created_by_id")
  private Long createdById;

  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  @Column(name = "profile_photo_url")
  private String profilePhotoUrl;

  protected UserAccountEntity() {}

  public UserAccountEntity(
      Long roleId, String name, String email, String passwordHash, Long createdById) {
    this.roleId = roleId;
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.createdById = createdById;
  }

  public Long getId() {
    return id;
  }
}
