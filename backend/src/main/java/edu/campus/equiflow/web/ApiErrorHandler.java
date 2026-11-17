package edu.campus.equiflow.web;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

  public record ErrorView(
      Instant timestamp, int status, String message, Map<String, String> fields) {}

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorView> business(ApiException ex) {
    return error(ex.status(), ex.getMessage(), Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorView> validation(MethodArgumentNotValidException ex) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
    return error(HttpStatus.BAD_REQUEST, "Check the highlighted fields and try again.", fields);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class
  })
  ResponseEntity<ErrorView> invalid(Exception ex) {
    return error(HttpStatus.BAD_REQUEST, "The request contains an invalid value.", Map.of());
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class,
    PessimisticLockingFailureException.class
  })
  ResponseEntity<ErrorView> conflict(Exception ex) {
    return error(
        HttpStatus.CONFLICT,
        "The record changed or conflicts with another record. Refresh and try again.",
        Map.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorView> denied(AccessDeniedException ex) {
    return error(
        HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorView> unexpected(Exception ex) {
    log.error("Unhandled request failure", ex);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "The request could not be completed. Please try again.",
        Map.of());
  }

  private ResponseEntity<ErrorView> error(
      HttpStatus status, String message, Map<String, String> fields) {
    return ResponseEntity.status(status)
        .body(new ErrorView(Instant.now(), status.value(), message, fields));
  }
}
