package com.quistock.ds_backend.handler;

import com.quistock.ds_backend.exception.ActionNotFoundException;
import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.exception.FlowNotFoundException;
import com.quistock.ds_backend.exception.InvalidActionStatusException;
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

  @ExceptionHandler(FlowNotFoundException.class)
  public ResponseEntity<ErrorDTO> handleFlowNotFoundException(FlowNotFoundException exception) {
    ErrorDTO error = new ErrorDTO("FLOW_NOT_FOUND", "Flow was not found for the provided ID.");
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  @ExceptionHandler(ActionNotFoundException.class)
  public ResponseEntity<ErrorDTO> handleActionNotFoundException(ActionNotFoundException exception) {
    ErrorDTO error = new ErrorDTO("ACTION_NOT_FOUND", "Action was not found for the provided ID.");
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  @ExceptionHandler(InvalidActionStatusException.class)
  public ResponseEntity<ErrorDTO> handleInvalidActionStatusException(
      InvalidActionStatusException exception) {
    ErrorDTO error = new ErrorDTO("INVALID_REQUEST", exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(ErpIntegrationException.class)
  public ResponseEntity<ErrorDTO> handleErpIntegrationException(ErpIntegrationException exception) {
    ErrorDTO error = new ErrorDTO("ERP_UNAVAILABLE", exception.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
  }
}
