package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.repository.ProductRepository;
import com.quistock.ds_backend.repository.StoreScope;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ProductService {
  private final ProductRepository productRepository;
  private final ErpSyncService erpSyncService;

  public ProductService(
      ProductRepository productRepository, Optional<ErpSyncService> erpSyncService) {
    this.productRepository = productRepository;
    this.erpSyncService = erpSyncService.orElse(null);
  }

  public List<ProductDTO> listProducts(
      String branch, String category, Boolean status, StoreScope scope) {
    synchronizeIfNeeded();
    return productRepository.findAll(branch, category, status, scope);
  }

  public ProductDTO findProductById(String id, StoreScope scope) {
    synchronizeIfNeeded();
    return productRepository
        .findByPublicId(id, scope)
        .orElseThrow(() -> new ProductNotFoundException(id));
  }

  private void synchronizeIfNeeded() {
    if (!productRepository.hasSnapshot()
        && erpSyncService != null
        && !erpSyncService.hasCompletedSync()) {
      erpSyncService.syncNow();
    }
  }
}
