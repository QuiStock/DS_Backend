package com.quistock.ds_backend.handler;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.model.dto.ErroDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ErpIntegrationException.class)
  public ResponseEntity<ErroDTO> handleErpIntegrationException(ErpIntegrationException exception) {
    ErroDTO error = new ErroDTO("ERP_INDISPONIVEL", exception.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
  }
}
