package com.quistock.ds_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "analysis.schedule.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DailyAnalysisScheduler {
  private static final Logger LOGGER = LoggerFactory.getLogger(DailyAnalysisScheduler.class);
  private final DailyAnalysisService analysis;

  public DailyAnalysisScheduler(DailyAnalysisService analysis) {
    this.analysis = analysis;
  }

  @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
  public void analyzeOnSchedule() {
    if (analysis.runDaily()) {
      LOGGER.info("Daily sales analysis completed.");
    }
  }
}
