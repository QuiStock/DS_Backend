package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.BranchDTO;
import com.quistock.ds_backend.repository.BranchRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BranchService {
  private final BranchRepository branchRepository;

  public BranchService(BranchRepository branchRepository) {
    this.branchRepository = branchRepository;
  }

  public List<BranchDTO> listBranches() {
    return branchRepository.findActiveBranches();
  }
}
