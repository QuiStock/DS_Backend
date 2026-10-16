package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.BranchDTO;
import com.quistock.ds_backend.repository.BranchRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService {
  private final BranchRepository branchRepository;
  private final UserAccessGuard accessGuard;

  public BranchService(BranchRepository branchRepository, UserAccessGuard accessGuard) {
    this.branchRepository = branchRepository;
    this.accessGuard = accessGuard;
  }

  @Transactional(readOnly = true)
  public List<BranchDTO> listBranches(long actorId) {
    var actor = accessGuard.requireRoleOrActive(actorId);
    Long regionId =
        UserManagementRules.REGIONAL_MANAGER.equals(actor.role())
            ? accessGuard.requireManagerManagement(actorId)
            : null;
    return branchRepository.findActiveBranches(regionId);
  }
}
