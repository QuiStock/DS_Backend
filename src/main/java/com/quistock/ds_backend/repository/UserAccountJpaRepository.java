package com.quistock.ds_backend.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, Long> {
  Optional<UserAccountEntity> findByEmail(String email);
}
