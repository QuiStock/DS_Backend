package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.ActionListItemDTO;
import com.quistock.ds_backend.model.dto.GenerateActionsRequest;
import com.quistock.ds_backend.model.dto.GenerateActionsResponse;
import com.quistock.ds_backend.service.ActionService;
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

  @GetMapping
  public List<ActionListItemDTO> listActions(
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "action_type", required = false) String actionType,
      @RequestParam(name = "flow_id", required = false) String flowId) {
    return actionService.listActions(status, actionType, flowId);
  }
}
