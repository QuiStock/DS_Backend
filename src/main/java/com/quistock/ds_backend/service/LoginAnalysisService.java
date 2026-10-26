package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.BranchDTO;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.repository.BranchRepository;
import com.quistock.ds_backend.repository.ErpProjectionLock;
import com.quistock.ds_backend.repository.FlowRepository;
import com.quistock.ds_backend.repository.StoreScope;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAnalysisService {
  private final BranchRepository branches;
  private final FlowRepository repository;
  private final ErpProjectionLock projectionLock;
  private final ProductService products;
  private final FlowService flows;

  public LoginAnalysisService(
      BranchRepository branches,
      FlowRepository repository,
      ProductService products,
      FlowService flows,
      ErpProjectionLock projectionLock) {
    this.projectionLock = projectionLock;
    this.branches = branches;
    this.repository = repository;
    this.products = products;
    this.flows = flows;
  }

  @Transactional(timeout = 120)
  public List<FlowDTO> analyzeLogin(UUID loginId, StoreScope scope) {
    projectionLock.acquire();
    products.listProducts(null, null, null, scope);
    // Stable store order prevents deadlocks between users with overlapping regions.
    var stores =
        branches.findActiveBranches(scope).stream()
            .sorted(Comparator.comparingLong(BranchDTO::storeId))
            .toList();
    for (var store : stores) {
      StoreScope singleStore = new StoreScope(scope.actorId(), store.storeId(), scope.regionId());
      if (repository.claimLogin(loginId, store.storeId(), singleStore)) {
        for (var product : products.listProducts(null, null, null, singleStore)) {
          flows.analyze(product, singleStore);
        }
      }
    }
    return flows.currentFlows(null, scope);
  }
}
