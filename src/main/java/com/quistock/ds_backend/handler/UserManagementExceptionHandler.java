package com.quistock.ds_backend.handler;

import com.quistock.ds_backend.exception.AssignmentTargetNotFoundException;
import com.quistock.ds_backend.exception.ConflictException;
import com.quistock.ds_backend.exception.UserNotFoundException;
import com.quistock.ds_backend.model.dto.ErrorDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UserManagementExceptionHandler {
  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<ErrorDTO> handleUserNotFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorDTO("USER_NOT_FOUND", "User was not found for the provided ID."));
  }

  @ExceptionHandler(AssignmentTargetNotFoundException.class)
  public ResponseEntity<ErrorDTO> handleAssignmentTargetNotFound(
      AssignmentTargetNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorDTO("ASSIGNMENT_TARGET_NOT_FOUND", exception.getMessage()));
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorDTO> handleConflict(ConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorDTO("CONFLICT", exception.getMessage()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorDTO> handleAccessDenied() {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorDTO("FORBIDDEN", "The authenticated user cannot perform this operation."));
  }
}
