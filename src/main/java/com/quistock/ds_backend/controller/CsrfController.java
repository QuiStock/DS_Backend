package com.quistock.ds_backend.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
  @GetMapping("/csrf")
  public ResponseEntity<CsrfResponse> token(CsrfToken csrf) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new CsrfResponse(csrf.getHeaderName(), csrf.getToken()));
  }

  public record CsrfResponse(String headerName, String token) {}
}
