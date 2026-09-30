package com.quistock.ds_backend.exception;

import java.io.Serial;

public class InvalidRequestException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public InvalidRequestException() {
    super("Required fields are missing or invalid.");
  }
}
