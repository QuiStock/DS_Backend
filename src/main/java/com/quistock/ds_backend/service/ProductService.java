package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.repository.ProductRepository;
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

  public List<ProductDTO> listProducts(String branch, String category, Boolean status) {
    synchronizeIfNeeded();
    return productRepository.findAll(branch, category, status);
  }

  public ProductDTO findProductById(String id) {
    synchronizeIfNeeded();
    return productRepository.findByPublicId(id).orElseThrow(() -> new ProductNotFoundException(id));
  }

  private void synchronizeIfNeeded() {
    if (!productRepository.hasSnapshot()
        && erpSyncService != null
        && !erpSyncService.hasCompletedSync()) {
      erpSyncService.syncNow();
    }
  }
}
