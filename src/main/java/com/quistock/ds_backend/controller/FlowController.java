package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.AnalyzeProductRequest;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.service.FlowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/flows")
public class FlowController {
  private final FlowService flowService;

  public FlowController(FlowService service) {
    this.flowService = service;
  }

  @PostMapping("/analyze")
  public ResponseEntity<FlowDTO> analyzeProduct(@Valid @RequestBody AnalyzeProductRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(flowService.analyzeProduct(request.productId()));
  }
}
