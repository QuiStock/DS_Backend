package com.quistock.ds_backend.controller;

import com.quistock.ds_backend.model.dto.CsrfTokenDTO;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
  @GetMapping("/csrf")
  public CsrfTokenDTO csrfToken(CsrfToken csrfToken) {
    return new CsrfTokenDTO(csrfToken.getToken());
  }
}
