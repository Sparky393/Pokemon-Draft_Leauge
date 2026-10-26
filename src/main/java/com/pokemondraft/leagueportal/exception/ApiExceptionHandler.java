package com.pokemondraft.leagueportal.exception;

import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of("message", message));
  }

  /** Keep messages from league rules instead of Spring's generic error pages. */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> leagueRule(ResponseStatusException e) {
    String reason = e.getReason();
    String message = reason != null && !reason.isBlank() ? reason
        : switch (e.getStatusCode().value()) {
        case 401 -> "Please log in again to continue.";
        case 403 -> "You do not have permission to perform this action.";
        case 404 -> "The requested item was not found.";
        case 409 -> "This action conflicts with the current league state. Refresh and try again.";
        default -> "That action could not be completed. Check your selections.";
        };
    return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
    return error(HttpStatus.NOT_FOUND,
        "The requested league item could not be found. Refresh the page and try again.");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Map<String, String>> badPath(MethodArgumentTypeMismatchException e) {
    return error(HttpStatus.BAD_REQUEST,
        "One of the supplied values is invalid. Refresh the page and try again.");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, String>> badBody(HttpMessageNotReadableException e) {
    return error(HttpStatus.BAD_REQUEST,
        "The submitted information could not be read. Check the fields and try again.");
  }

  @ExceptionHandler(DateTimeParseException.class)
  public ResponseEntity<Map<String, String>> badDate(DateTimeParseException e) {
    return error(HttpStatus.BAD_REQUEST, "The lineup deadline has an invalid date or time.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException e) {
    return error(HttpStatus.CONFLICT,
        "That change conflicts with existing league data. Refresh the page and try again.");
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> illegalState(IllegalStateException e) {
    String m = e.getMessage();
    return error(HttpStatus.BAD_REQUEST,
        m == null || m.isBlank() ? "That action cannot be completed in the league's current state."
            : m);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> illegalArgument(IllegalArgumentException e) {
    String m = e.getMessage();
    return error(HttpStatus.BAD_REQUEST,
        m == null || m.isBlank() ? "One of the supplied values is invalid." : m);
  }
}
