package com.quistock.ds_backend.exception;

import java.io.Serial;

public class ActionNotFoundException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public ActionNotFoundException(String id) {
    super("Action was not found for the provided ID: " + id);
  }
}
