package com.quistock.ds_backend.handler;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.exception.ProductNotFoundException;
import com.quistock.ds_backend.model.dto.ErrorDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ProductNotFoundException.class)
  public ResponseEntity<ErrorDTO> handleProductNotFoundException(
      ProductNotFoundException exception) {
    ErrorDTO error =
        new ErrorDTO("PRODUCT_NOT_FOUND", "Product was not found for the provided ID.");
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  @ExceptionHandler(ErpIntegrationException.class)
  public ResponseEntity<ErrorDTO> handleErpIntegrationException(ErpIntegrationException exception) {
    ErrorDTO error = new ErrorDTO("ERP_UNAVAILABLE", exception.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
  }
}
