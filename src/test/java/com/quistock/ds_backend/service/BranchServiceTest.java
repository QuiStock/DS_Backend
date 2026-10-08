package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.model.dto.BranchDTO;
import com.quistock.ds_backend.repository.BranchRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BranchServiceTest {
  private BranchRepository branchRepository;
  private BranchService branchService;

  @BeforeEach
  void setUp() {
    branchRepository = mock(BranchRepository.class);
    branchService = new BranchService(branchRepository);
  }

  @Test
  void shouldListActiveBranchesFromTheRepository() {
    List<BranchDTO> branches =
        List.of(
            new BranchDTO(
                "FIL001", 51L, "Santana Store", "Rua A, 10", "Sao Paulo", "SP", null, null));
    when(branchRepository.findActiveBranches()).thenReturn(branches);

    assertThat(branchService.listBranches()).containsExactlyElementsOf(branches);
  }

  @Test
  void shouldReturnAnEmptyListWhenThereAreNoBranches() {
    when(branchRepository.findActiveBranches()).thenReturn(List.of());

    assertThat(branchService.listBranches()).isEmpty();
  }
}
