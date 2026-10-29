package com.quistock.ds_backend.exception;

import org.springframework.security.access.AccessDeniedException;

public final class NoActiveStoreException extends AccessDeniedException {
  private static final long serialVersionUID = 1L;

  public NoActiveStoreException() {
    super("The account has no active store assignment.");
  }
}
