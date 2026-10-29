package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.repository.BranchRepository;
import com.quistock.ds_backend.repository.StoreScope;
import com.quistock.ds_backend.repository.UserAccountReadRepository;
import com.quistock.ds_backend.repository.UserAccountReadRepository.Actor;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

class UserAccessGuardTest {
  private final UserAccountReadRepository users = mock(UserAccountReadRepository.class);
  private final BranchRepository branches = mock(BranchRepository.class);
  private final UserAccessGuard guard = new UserAccessGuard(users, branches);

  @ParameterizedTest
  @ValueSource(strings = {"EMPLOYEE", "MANAGER"})
  void restrictsStoreRolesToTheirActiveStore(String role) {
    actor(role, "ACTIVE");
    when(users.findActiveStoreAssignment(1001L)).thenReturn(Optional.of(51L));

    assertThat(guard.requireStoreScope(1001L, null)).isEqualTo(new StoreScope(1001L, 51L, null));
    assertThatThrownBy(() -> guard.requireStoreScope(1001L, 52L))
        .isInstanceOf(AccessDeniedException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"EMPLOYEE", "MANAGER"})
  void rejectsStoreRolesWithoutAnActiveAssignment(String role) {
    actor(role, "ACTIVE");

    assertThatThrownBy(() -> guard.requireStoreScope(1001L, null))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("The account has no active store assignment.")
        .satisfies(
            error ->
                assertThat(error.getClass().getSimpleName()).isEqualTo("NoActiveStoreException"));
  }

  @Test
  void restrictsRegionalManagerToTheirActiveRegion() {
    actor("REGIONAL_MANAGER", "ACTIVE");
    when(users.findActiveRegionalAssignment(1001L)).thenReturn(Optional.of(3L));

    assertThat(guard.requireStoreScope(1001L, null)).isEqualTo(new StoreScope(1001L, null, 3L));
    assertThatThrownBy(() -> guard.requireStoreScope(1001L, 52L))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void rejectsRegionalManagerWithoutAnActiveRegion() {
    actor("REGIONAL_MANAGER", "ACTIVE");

    assertThatThrownBy(() -> guard.requireStoreScope(1001L, null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"EMPLOYEE", "MANAGER", "REGIONAL_MANAGER"})
  void rejectsInactiveAccounts(String role) {
    actor(role, "INACTIVE");

    assertThatThrownBy(() -> guard.requireStoreScope(1001L, null))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("The account is inactive.");
  }

  @Test
  void preservesAdministratorGlobalScope() {
    actor("ADMIN", "ACTIVE");

    assertThat(guard.requireStoreScope(1001L, null)).isEqualTo(new StoreScope(1001L, null, null));
  }

  private void actor(String role, String status) {
    when(users.findActor(1001L)).thenReturn(Optional.of(new Actor(1001L, role, status)));
  }
}
