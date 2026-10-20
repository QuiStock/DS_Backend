package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.FlowNotFoundException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.repository.FlowRepository;
import com.quistock.ds_backend.repository.StoreScope;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class FlowService {
  private static final Set<String> VALID_FLOW_TYPES = Set.of("HIGH", "MEDIUM", "LOW");
  private static final Set<String> VALID_STATUSES = Set.of("ANALYZED");

  private final ProductService productService;
  private final FlowRepository flowRepository;
  private final FlowAnalysisCalculator analysisCalculator;
  private final Clock clock;

  public FlowService(
      ProductService productService,
      FlowRepository flowRepository,
      FlowAnalysisCalculator analysisCalculator,
      Clock clock) {
    this.productService = productService;
    this.flowRepository = flowRepository;
    this.analysisCalculator = analysisCalculator;
    this.clock = clock;
  }

  public FlowDTO analyzeProduct(String productId, StoreScope scope) {
    ProductDTO product = productService.findProductById(productId, scope);
    FlowAnalysisCalculator.Result calculation = analysisCalculator.calculate(product);
    Instant analysisDate = Instant.now(clock);
    FlowRepository.AnalysisSnapshot snapshot =
        new FlowRepository.AnalysisSnapshot(
            product.id(),
            calculation.flowType(),
            calculation.dailySalesAverage(),
            calculation.stockCoverageDays(),
            calculation.currentStock(),
            calculation.minimumStock(),
            calculation.sales7d(),
            calculation.sales30d(),
            product.expirationDays(),
            product.supplierLeadTime(),
            calculation.reason(),
            analysisDate);
    long id = flowRepository.insertAnalysis(snapshot, scope);
    if (id < 0) {
      throw new ProductNotFoundException(productId);
    }
    return new FlowDTO(
        Long.toString(id),
        product.id(),
        product.name(),
        calculation.flowType(),
        "ANALYZED",
        calculation.reason(),
        calculation.dailySalesAverage(),
        calculation.stockCoverageDays(),
        product.expirationDays(),
        product.supplierLeadTime(),
        analysisDate);
  }

  public List<FlowDTO> listFlows(StoreScope scope) {
    return listFlows(null, null, null, scope);
  }

  public List<FlowDTO> listFlows(
      String flowType, String productId, String status, StoreScope scope) {
    if (flowType != null && !VALID_FLOW_TYPES.contains(flowType)) {
      throw new InvalidRequestException();
    }
    if (status != null && !VALID_STATUSES.contains(status)) {
      throw new InvalidRequestException();
    }
    return flowRepository.findAll(flowType, productId, status, scope);
  }

  public FlowDTO findFlowById(String id, StoreScope scope) {
    return flowRepository.findById(id, scope).orElseThrow(() -> new FlowNotFoundException(id));
  }
}
