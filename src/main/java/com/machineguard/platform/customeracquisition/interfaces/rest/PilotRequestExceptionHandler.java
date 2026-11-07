package com.machineguard.platform.customeracquisition.interfaces.rest;

import com.machineguard.platform.customeracquisition.application.PilotRequestThrottledException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.machineguard.platform.customeracquisition")
public class PilotRequestExceptionHandler {
    @ExceptionHandler(PilotRequestThrottledException.class)
    public ResponseEntity<Map<String, Object>> throttled(PilotRequestThrottledException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", Long.toString(exception.retryAfter()))
                .body(Map.of("status", 429, "error", "Too Many Requests", "message", "Too many pilot requests; retry later"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException error) {
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", status.getReasonPhrase(),
                "message", error.getReason() == null ? "Request failed" : error.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getFieldErrors().stream().findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage()).orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("status", 400, "error", "Bad Request", "message", message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", 409, "error", "Conflict",
                "message", "A conflicting pilot request already exists; retry to receive the existing request"));
    }
}
