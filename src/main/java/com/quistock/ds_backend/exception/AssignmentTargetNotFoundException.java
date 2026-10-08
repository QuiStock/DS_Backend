package com.quistock.ds_backend.exception;

import java.io.Serial;

public class AssignmentTargetNotFoundException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public AssignmentTargetNotFoundException() {
    super("The selected active branch or region was not found.");
  }
}
