package com.quistock.ds_backend.exception;

import java.io.Serial;

public class ErpIntegrationException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1233455789;

  public ErpIntegrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
