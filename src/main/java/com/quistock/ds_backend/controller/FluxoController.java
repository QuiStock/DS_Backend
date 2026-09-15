package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.AnalisarProdutoRequest;
import com.quistock.ds_backend.model.dto.FluxoDTO;
import com.quistock.ds_backend.service.FluxoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fluxos")
public class FluxoController {
  private final FluxoService fluxoService;

  public FluxoController(FluxoService service) {
    this.fluxoService = service;
  }

  @PostMapping("/analisar")
  public ResponseEntity<FluxoDTO> analisarProduto(
      @Valid @RequestBody AnalisarProdutoRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(fluxoService.analisarProduto(request.produtoId()));
  }
}
