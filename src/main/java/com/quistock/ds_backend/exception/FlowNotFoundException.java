package com.quistock.ds_backend.exception;

import java.io.Serial;

public class FlowNotFoundException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public FlowNotFoundException(String id) {
    super("Flow was not found for the provided ID: " + id);
  }
}
