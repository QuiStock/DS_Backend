package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.AnalyzeProductRequest;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.service.FlowService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  @GetMapping
  public List<FlowDTO> listFlows(
      @RequestParam(name = "flow_type", required = false) String flowType,
      @RequestParam(name = "product_id", required = false) String productId,
      @RequestParam(name = "status", required = false) String status) {
    return flowService.listFlows(flowType, productId, status);
  }
}
