package com.quistock.ds_backend.service;

import com.quistock.ds_backend.exception.FlowNotFoundException;
import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.repository.AnalysisExecutionRepository;
import com.quistock.ds_backend.repository.ErpProjectionLock;
import com.quistock.ds_backend.repository.ProductRepository;
import com.quistock.ds_backend.repository.StoreScope;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DailyAnalysisService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
  private final AnalysisExecutionRepository executions;
  private final ProductRepository products;
  private final FlowService flows;
  private final ErpProjectionLock projectionLock;
  private final Clock clock;
  private final TransactionTemplate coordination;
  private final TransactionTemplate processing;

  public DailyAnalysisService(
      AnalysisExecutionRepository executions,
      ProductRepository products,
      FlowService flows,
      ErpProjectionLock projectionLock,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.executions = executions;
    this.products = products;
    this.flows = flows;
    this.projectionLock = projectionLock;
    this.clock = clock;
    coordination = new TransactionTemplate(transactionManager);
    processing = new TransactionTemplate(transactionManager);
    processing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    processing.setTimeout(120);
  }

  public boolean runDaily() {
    return Boolean.TRUE.equals(coordination.execute(status -> runLocked()));
  }

  private boolean runLocked() {
    if (!executions.tryDailyLock()) {
      return false;
    }
    LocalDate date = LocalDate.now(clock.withZone(BUSINESS_ZONE));
    if (executions.completedDailyRun(date)) {
      return false;
    }
    long executionId =
        executions.start(AnalysisExecutionRepository.DAILY_MODEL_VERSION, date, clock.instant());
    try {
      processing.executeWithoutResult(transaction -> process(executionId));
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
    return true;
  }

  private void process(long executionId) {
    projectionLock.acquire();
    StoreScope scope = new StoreScope(0L, null, null);
    if (!products.hasSnapshot()) {
      throw new IllegalStateException("Sales analysis requires a synchronized ERP snapshot.");
    }
    for (var product : products.findAll(null, null, null, scope)) {
      flows.analyze(product, scope, executionId);
    }
    executions.complete(executionId);
  }
}
