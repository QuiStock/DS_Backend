package com.quistock.ds_backend.exception;

import java.io.Serial;

public class ConflictException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public ConflictException() {
    super("The request conflicts with an existing resource or assignment.");
  }

  public ConflictException(Throwable cause) {
    super("The request conflicts with an existing resource or assignment.", cause);
  }
}
