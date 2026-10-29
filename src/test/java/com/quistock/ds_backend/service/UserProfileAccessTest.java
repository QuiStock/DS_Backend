package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.repository.BranchRepository;
import com.quistock.ds_backend.repository.RegionRepository;
import com.quistock.ds_backend.repository.UserAccountReadRepository;
import com.quistock.ds_backend.repository.UserAccountReadRepository.Actor;
import com.quistock.ds_backend.repository.UserAccountWriteRepository;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

class UserProfileAccessTest {
  @ParameterizedTest
  @ValueSource(strings = {"EMPLOYEE", "MANAGER"})
  void profileRequiresAnActiveStoreForStoreRoles(String role) {
    var users = mock(UserAccountReadRepository.class);
    when(users.findActor(1001L)).thenReturn(Optional.of(new Actor(1001L, role, "ACTIVE")));
    var guard = new UserAccessGuard(users, mock(BranchRepository.class));
    var profiles =
        new UserProfileService(
            users, mock(RegionRepository.class), guard, mock(UserAccountWriteRepository.class));
    assertThatThrownBy(() -> profiles.profile(1001L))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("The account has no active store assignment.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMIN", "REGIONAL_MANAGER"})
  void doesNotRequireAStoreForOtherProfileRoles(String role) {
    var users = mock(UserAccountReadRepository.class);
    when(users.findActor(1001L)).thenReturn(Optional.of(new Actor(1001L, role, "ACTIVE")));
    var branches = mock(BranchRepository.class);
    var profiles =
        new UserProfileService(
            users,
            mock(RegionRepository.class),
            new UserAccessGuard(users, branches),
            mock(UserAccountWriteRepository.class));
    assertThatThrownBy(() -> profiles.profile(1001L))
        .isInstanceOf(com.quistock.ds_backend.exception.UserNotFoundException.class);
    verifyNoInteractions(branches);
  }

  @org.junit.jupiter.api.Test
  void distinguishesStoreLossFromOtherForbiddenResponses() {
    var handler = new com.quistock.ds_backend.handler.UserManagementExceptionHandler();
    assertThat(handler.handleNoActiveStore().getBody().error()).isEqualTo("NO_ACTIVE_STORE");
    assertThat(handler.handleAccessDenied().getBody().error()).isEqualTo("FORBIDDEN");
  }
}
