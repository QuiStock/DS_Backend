package com.quistock.ds_backend.exception;

import java.io.Serial;

public class InvalidActionStatusException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public InvalidActionStatusException(String status) {
    super("Unsupported action status: " + status);
  }
}
