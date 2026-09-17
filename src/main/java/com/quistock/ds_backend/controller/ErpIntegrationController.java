package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.ErpIntegrationStatusDTO;
import com.quistock.ds_backend.service.ErpIntegrationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/erp-integration")
public class ErpIntegrationController {
  private final ErpIntegrationService erpIntegrationService;

  public ErpIntegrationController(ErpIntegrationService service) {
    this.erpIntegrationService = service;
  }

  @GetMapping("/status")
  public ErpIntegrationStatusDTO getStatus() {
    return erpIntegrationService.getStatus();
  }
}
