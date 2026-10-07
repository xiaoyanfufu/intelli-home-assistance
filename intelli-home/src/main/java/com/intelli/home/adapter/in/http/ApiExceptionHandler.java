package com.intelli.home.adapter.in.http;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
  @ExceptionHandler(com.intelli.home.application.service.VersionConflictException.class)
  public ResponseEntity<?> conflict(RuntimeException e) {
    return ResponseEntity.status(409)
        .body(Map.of("error", "VERSION_CONFLICT", "message", e.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> invalid(IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "INVALID_REQUEST", "message", e.getMessage()));
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<?> unavailable(DataAccessException e) {
    log.error("Storage unavailable", e);
    return ResponseEntity.status(503).body(Map.of("error", "STORAGE_UNAVAILABLE"));
  }
}
