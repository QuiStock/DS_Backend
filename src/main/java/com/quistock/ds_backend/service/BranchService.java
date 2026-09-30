package com.quistock.ds_backend.service;

import com.quistock.ds_backend.model.dto.BranchDTO;
import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.repository.BranchRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BranchService {
  private final ProductService productService;
  private final BranchRepository branchRepository;

  @Autowired
  public BranchService(ProductService service, BranchRepository repository) {
    this.productService = service;
    this.branchRepository = repository;
  }

  // Kept for the existing service-level tests.
  public BranchService(ProductService service) {
    this.productService = service;
    this.branchRepository = null;
  }

  public List<BranchDTO> listBranches() {
    if (branchRepository != null) {
      return branchRepository.findActiveBranches();
    }
    Map<String, String> branches =
        productService.listProducts(null, null, null).stream()
            .filter(product -> product.branch() != null)
            .collect(
                Collectors.toMap(
                    this::branchId,
                    ProductDTO::branch,
                    (firstName, ignoredName) -> firstName,
                    LinkedHashMap::new));

    return branches.entrySet().stream()
        .map(entry -> new BranchDTO(entry.getKey(), entry.getValue(), null, null, null, null, null))
        .toList();
  }

  private String branchId(ProductDTO product) {
    String productId = Objects.toString(product.id(), "");
    int separatorIndex = productId.indexOf(':');
    return separatorIndex >= 0 ? productId.substring(separatorIndex + 1) : productId;
  }
}
