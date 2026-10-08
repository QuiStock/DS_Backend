package com.quistock.ds_backend.exception;

import java.io.Serial;

public class UserNotFoundException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public UserNotFoundException() {
    super("User was not found for the provided ID.");
  }
}
