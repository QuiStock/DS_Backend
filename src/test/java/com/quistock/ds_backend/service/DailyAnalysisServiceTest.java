package com.quistock.ds_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quistock.ds_backend.model.dto.ProductDTO;
import com.quistock.ds_backend.repository.AnalysisExecutionRepository;
import com.quistock.ds_backend.repository.ErpProjectionLock;
import com.quistock.ds_backend.repository.ProductRepository;
import com.quistock.ds_backend.repository.StoreScope;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class DailyAnalysisServiceTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-30T01:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 29);
  private final AnalysisExecutionRepository executions = mock(AnalysisExecutionRepository.class);
  private final ProductRepository products = mock(ProductRepository.class);
  private final FlowService flows = mock(FlowService.class);
  private final ErpProjectionLock projectionLock = mock(ErpProjectionLock.class);
  private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
  private DailyAnalysisService service;

  @BeforeEach
  void setUp() {
    when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    when(executions.tryDailyLock()).thenReturn(true);
    when(executions.start(any(), any(), any())).thenReturn(42L);
    when(products.hasSnapshot()).thenReturn(true);
    service =
        new DailyAnalysisService(executions, products, flows, projectionLock, CLOCK, transactions);
  }

  @Test
  void shouldProcessAllActiveProductsAndLinkTheExecutionUsingSaoPauloDate() {
    var product = mock(ProductDTO.class);
    var scope = new StoreScope(0L, null, null);
    when(products.findAll(null, null, null, scope)).thenReturn(List.of(product));
    assertThat(service.runDaily()).isTrue();
    verify(executions)
        .start(AnalysisExecutionRepository.DAILY_MODEL_VERSION, BUSINESS_DATE, CLOCK.instant());
    verify(projectionLock).acquire();
    verify(flows).analyze(product, scope, 42L);
    verify(executions).complete(42L);
    verify(executions, never()).fail(anyLong());
  }

  @Test
  void shouldSkipWhenAnotherInstanceIsProcessing() {
    when(executions.tryDailyLock()).thenReturn(false);
    assertThat(service.runDaily()).isFalse();
    verify(executions, never()).start(any(), any(), any());
    verifyNoInteractions(products, flows, projectionLock);
  }

  @Test
  void shouldReuseCompletedDailyExecutionAcrossUsersAndInstances() {
    when(executions.completedDailyRun(BUSINESS_DATE)).thenReturn(true);
    assertThat(service.runDaily()).isFalse();
    verify(executions, never()).start(any(), any(), any());
    verifyNoInteractions(products, flows, projectionLock);
  }

  @Test
  void shouldRollbackProcessingAndRecordFailure() {
    var product = mock(ProductDTO.class);
    var scope = new StoreScope(0L, null, null);
    when(products.findAll(null, null, null, scope)).thenReturn(List.of(product));
    when(flows.analyze(product, scope, 42L))
        .thenThrow(new IllegalStateException("Processing failed"));
    assertThatThrownBy(service::runDaily).hasMessage("Processing failed");
    verify(executions).fail(42L);
    verify(executions, never()).complete(anyLong());
    verify(transactions, atLeastOnce()).rollback(any());
  }

  @Test
  void shouldNotCompleteAnExecutionWithoutAnErpSnapshot() {
    when(products.hasSnapshot()).thenReturn(false);
    assertThatThrownBy(service::runDaily).isInstanceOf(IllegalStateException.class);
    verify(executions).fail(42L);
    verifyNoInteractions(flows);
  }

  @Test
  void shouldPreserveProcessingFailureIfRecordingStatusAlsoFails() {
    when(products.hasSnapshot()).thenReturn(false);
    doThrow(new IllegalStateException("Database unavailable")).when(executions).fail(42L);
    assertThatThrownBy(service::runDaily)
        .hasMessageContaining("synchronized ERP snapshot")
        .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));
  }

  @Test
  void shouldScheduleEveryDayAtEightInSaoPaulo() throws Exception {
    Scheduled schedule =
        DailyAnalysisScheduler.class.getMethod("analyzeOnSchedule").getAnnotation(Scheduled.class);
    assertThat(schedule.zone()).isEqualTo("America/Sao_Paulo");
    var next =
        CronExpression.parse(schedule.cron())
            .next(ZonedDateTime.parse("2026-10-30T07:59:59-03:00[America/Sao_Paulo]"));
    assertThat(next).isEqualTo(ZonedDateTime.parse("2026-10-30T08:00:00-03:00[America/Sao_Paulo]"));
    assertThat(CronExpression.parse(schedule.cron()).next(next)).isEqualTo(next.plusDays(1));
  }

  @Test
  void shouldPropagateFailureToTheSpringSchedulerErrorHandler() {
    var analysis = mock(DailyAnalysisService.class);
    var scheduler = new DailyAnalysisScheduler(analysis);
    when(analysis.runDaily())
        .thenThrow(new IllegalStateException("Database unavailable"))
        .thenReturn(true)
        .thenReturn(false);
    assertThatThrownBy(scheduler::analyzeOnSchedule).hasMessage("Database unavailable");
    scheduler.analyzeOnSchedule();
    scheduler.analyzeOnSchedule();
    verify(analysis, times(3)).runDaily();
  }
}
