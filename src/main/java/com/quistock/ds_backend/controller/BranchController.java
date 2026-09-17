package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.BranchDTO;
import com.quistock.ds_backend.service.BranchService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/branches")
public class BranchController {
  private final BranchService branchService;

  public BranchController(BranchService service) {
    this.branchService = service;
  }

  @GetMapping
  public List<BranchDTO> listBranches() {
    return branchService.listBranches();
  }
}
