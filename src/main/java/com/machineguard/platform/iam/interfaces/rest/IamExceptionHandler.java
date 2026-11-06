package com.machineguard.platform.iam.interfaces.rest;

import org.springframework.dao.DataIntegrityViolationException;
import com.machineguard.platform.iam.application.RefreshTokenReplayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.machineguard.platform.iam")
public class IamExceptionHandler {
    @ExceptionHandler(com.machineguard.platform.iam.application.LoginThrottledException.class)
    public ResponseEntity<Map<String,Object>> throttled(com.machineguard.platform.iam.application.LoginThrottledException exception) {
        return ResponseEntity.status(429).header("Retry-After",Long.toString(exception.retryAfter()))
                .body(Map.of("status",429,"message","Too many login attempts; retry later"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException error) {
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", status.getReasonPhrase(),
                "message", error.getReason() == null ? "Request failed" : error.getReason()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", 403, "error", "Forbidden", "message", error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getFieldErrors().stream().findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage()).orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("status", 400, "error", "Bad Request", "message", message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", 409, "error", "Conflict", "message", "A conflicting IAM record already exists"));
    }

    @ExceptionHandler(RefreshTokenReplayException.class)
    public ResponseEntity<Map<String, Object>> refreshReplay(RefreshTokenReplayException error) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", 401, "error", "Unauthorized",
                "message", "Refresh token is no longer valid; sign in again"));
    }
}
