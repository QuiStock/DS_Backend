package com.quistock.ds_backend.exception;

import java.io.Serial;

public class ProdutoNotFoundException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public ProdutoNotFoundException(String id) {
    super("Produto não encontrado para o ID informado: " + id);
  }
}
