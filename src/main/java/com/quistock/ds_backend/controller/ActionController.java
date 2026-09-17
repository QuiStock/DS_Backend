package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.GenerateActionsRequest;
import com.quistock.ds_backend.model.dto.GenerateActionsResponse;
import com.quistock.ds_backend.service.ActionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/actions")
public class ActionController {
  private final ActionService actionService;

  public ActionController(ActionService service) {
    this.actionService = service;
  }

  @PostMapping("/generate")
  public ResponseEntity<GenerateActionsResponse> generateActions(
      @Valid @RequestBody GenerateActionsRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(actionService.generateActions(request.flowId()));
  }
}
