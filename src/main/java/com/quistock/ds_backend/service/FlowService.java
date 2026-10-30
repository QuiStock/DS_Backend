package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.FlowNotFoundException;
import com.quistock.ds_backend.exception.InvalidRequestException;
import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.model.dto.FlowDTO;
import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.repository.AnalysisExecutionRepository;
import com.quistock.ds_backend.repository.ErpProjectionLock;
import com.quistock.ds_backend.repository.FlowRepository;
import com.quistock.ds_backend.repository.StoreScope;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlowService {
  private static final Set<String> VALID_FLOW_TYPES = Set.of("HIGH", "MEDIUM", "LOW");
  private static final Set<String> VALID_STATUSES = Set.of("ANALYZED", "INSUFFICIENT_DATA");
  private final ErpProjectionLock projectionLock;
  private final ProductService productService;
  private final FlowRepository flowRepository;
  private final FlowAnalysisCalculator analysisCalculator;
  private final Clock clock;
  private final AnalysisExecutionRepository executions;

  public FlowService(
      ProductService productService,
      FlowRepository flowRepository,
      FlowAnalysisCalculator analysisCalculator,
      Clock clock,
      ErpProjectionLock projectionLock,
      AnalysisExecutionRepository executions) {
    this.executions = executions;
    this.projectionLock = projectionLock;
    this.productService = productService;
    this.flowRepository = flowRepository;
    this.analysisCalculator = analysisCalculator;
    this.clock = clock;
  }

  @Transactional
  public FlowDTO analyzeProduct(String productId, StoreScope scope) {
    projectionLock.acquire();
    var product = productService.findProductById(productId, scope);
    long executionId =
        executions.start(FlowAnalysisCalculator.CLASSIFIER, LocalDate.now(clock), clock.instant());
    try {
      var result = analyze(product, scope, executionId);
      executions.complete(executionId);
      return result;
    } catch (DataAccessException
        | TransactionException
        | IllegalStateException
        | ProductNotFoundException
        | FlowNotFoundException exception) {
      try {
        executions.fail(executionId);
      } catch (DataAccessException | TransactionException | IllegalStateException statusException) {
        exception.addSuppressed(statusException);
      }
      throw exception;
    }
  }

  public FlowDTO analyze(ProductDTO product, StoreScope scope, long executionId) {
    var calculation =
        analysisCalculator.calculate(product, flowRepository.comparableSales(product.id(), scope));
    var snapshot =
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
            Instant.now(clock),
            calculation.risks());
    long id = flowRepository.insertAnalysis(snapshot, scope, executionId);
    if (id < 0) {
      throw new ProductNotFoundException(product.id());
    }
    return findFlowById(Long.toString(id), scope);
  }

  public List<FlowDTO> listFlows(StoreScope scope) {
    return listFlows(null, null, null, scope);
  }

  public List<FlowDTO> listFlows(
      String flowType, String productId, String status, StoreScope scope) {
    if (flowType != null && !VALID_FLOW_TYPES.contains(flowType)
        || status != null && !VALID_STATUSES.contains(status)) {
      throw new InvalidRequestException();
    }
    return flowRepository.findAll(flowType, productId, status, scope);
  }

  public List<FlowDTO> currentFlows(String productId, StoreScope scope) {
    return listFlows(null, productId, null, scope).stream().filter(FlowDTO::current).toList();
  }

  public FlowDTO findFlowById(String id, StoreScope scope) {
    return flowRepository.findById(id, scope).orElseThrow(() -> new FlowNotFoundException(id));
  }
}
