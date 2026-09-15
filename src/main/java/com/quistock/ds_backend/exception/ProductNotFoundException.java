package com.quistock.ds_backend.exception;

import java.io.Serial;

public class ProductNotFoundException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public ProductNotFoundException(String id) {
    super("Product was not found for the provided ID: " + id);
  }
}
