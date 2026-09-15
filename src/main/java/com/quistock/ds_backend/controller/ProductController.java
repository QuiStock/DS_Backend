package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.service.ProductService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService service) {
    this.productService = service;
  }

  @GetMapping
  public List<ProductDTO> listProducts(
      @RequestParam(name = "branch", required = false) String branch,
      @RequestParam(name = "category", required = false) String category,
      @RequestParam(name = "status", required = false) Boolean status) {
    return productService.listProducts(branch, category, status);
  }

  @GetMapping("/{id}")
  public ProductDTO findProductById(@PathVariable String id) {
    return productService.findProductById(id);
  }
}
