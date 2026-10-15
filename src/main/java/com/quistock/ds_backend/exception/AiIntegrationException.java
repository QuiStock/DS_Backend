package com.quistock.ds_backend.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

public class AiIntegrationException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;
  private final HttpStatus httpStatus;
  private final String errorCode;

  public AiIntegrationException(HttpStatus status, String code, String message) {
    this(status, code, message, null);
  }

  public AiIntegrationException(HttpStatus status, String code, String message, Throwable cause) {
    super(message, cause);
    this.httpStatus = status;
    this.errorCode = code;
  }

  public HttpStatus status() {
    return httpStatus;
  }

  public String code() {
    return errorCode;
  }
}
