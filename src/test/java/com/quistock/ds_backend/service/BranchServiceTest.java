package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.model.dto.BranchDTO;
import com.quistock.ds_backend.repository.BranchRepository;
import com.quistock.ds_backend.repository.UserAccountReadRepository.Actor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BranchServiceTest {
  private BranchRepository branchRepository;
  private BranchService branchService;
  private UserAccessGuard accessGuard;

  @BeforeEach
  void setUp() {
    branchRepository = mock(BranchRepository.class);
    accessGuard = mock(UserAccessGuard.class);
    when(accessGuard.requireRoleOrActive(1001L)).thenReturn(new Actor(1001L, "ADMIN", "ACTIVE"));
    branchService = new BranchService(branchRepository, accessGuard);
  }

  @Test
  void shouldListActiveBranchesFromTheRepository() {
    List<BranchDTO> branches =
        List.of(
            new BranchDTO(
                "FIL001", 51L, "Santana Store", "Rua A, 10", "Sao Paulo", "SP", null, null, 3L));
    when(branchRepository.findActiveBranches(null)).thenReturn(branches);

    assertThat(branchService.listBranches(1001L)).containsExactlyElementsOf(branches);
  }

  @Test
  void shouldReturnAnEmptyListWhenThereAreNoBranches() {
    when(accessGuard.requireRoleOrActive(1001L))
        .thenReturn(new Actor(1001L, "GERENTE_REGIONAL", "ACTIVE"));
    when(accessGuard.requireManagerManagement(1001L)).thenReturn(3L);
    when(branchRepository.findActiveBranches(3L)).thenReturn(List.of());

    assertThat(branchService.listBranches(1001L)).isEmpty();
    verify(branchRepository).findActiveBranches(3L);
  }
}
