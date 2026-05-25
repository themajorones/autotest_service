package dev.themajorones.ats.web.rest;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        LOG.warn("Request validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(error("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException ex) {
        LOG.warn("Request failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("REQUEST_FAILED", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception ex) {
        LOG.error("Unexpected request failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("INTERNAL_ERROR", "Unexpected request failure"));
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of(
            "status", "ERROR",
            "code", code,
            "message", message == null ? "Request failed" : message
        );
    }
}
