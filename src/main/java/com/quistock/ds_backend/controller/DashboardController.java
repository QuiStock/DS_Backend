package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.DashboardSummaryDTO;
import com.quistock.ds_backend.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {
  private final DashboardService dashboardService;

  public DashboardController(DashboardService service) {
    this.dashboardService = service;
  }

  @GetMapping("/summary")
  public DashboardSummaryDTO getSummary() {
    return dashboardService.getSummary();
  }
}
