package com.quistock.ds_backend.handler;

import com.quistock.ds_backend.exception.ErpIntegrationException;
import com.quistock.ds_backend.exception.ProdutoNotFoundException;
import com.quistock.ds_backend.model.dto.ErroDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ProdutoNotFoundException.class)
  public ResponseEntity<ErroDTO> handleProdutoNotFoundException(
      ProdutoNotFoundException exception) {
    ErroDTO error =
        new ErroDTO("PRODUTO_NAO_ENCONTRADO", "Produto não encontrado para o ID informado.");
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  @ExceptionHandler(ErpIntegrationException.class)
  public ResponseEntity<ErroDTO> handleErpIntegrationException(ErpIntegrationException exception) {
    ErroDTO error = new ErroDTO("ERP_INDISPONIVEL", exception.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
  }
}
