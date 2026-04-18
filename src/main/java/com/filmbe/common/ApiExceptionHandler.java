package com.filmbe.common;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Dữ liệu không hợp lệ.");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    ResponseEntity<Map<String, Object>> handleBadRequest(Exception exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException exception) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleNotFound(Exception exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Map<String, Object>> handleUnauthorized(BadCredentialsException exception) {
        return build(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng.");
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<Map<String, Object>> handleUpstream(RestClientException exception) {
        log.error("Upstream phimapi error", exception);
        return build(HttpStatus.BAD_GATEWAY, "Không thể lấy dữ liệu từ nguồn phim.");
    }

    @ExceptionHandler({
            HttpMessageNotWritableException.class,
            AsyncRequestNotUsableException.class
    })
    void handleClientDisconnect(RuntimeException exception) {
        if (isClientDisconnect(exception)) {
            log.debug("Client disconnected before response completed: {}", summarizeCause(exception));
            return;
        }
        throw exception;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        log.error("Unhandled backend error", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống.");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private boolean isClientDisconnect(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }

            String className = current.getClass().getName();
            if ("org.apache.catalina.connector.ClientAbortException".equals(className)) {
                return true;
            }

            if (current instanceof java.io.IOException && hasClientDisconnectMessage(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasClientDisconnectMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("broken pipe")
                || normalized.contains("connection reset by peer")
                || normalized.contains("forcibly closed")
                || normalized.contains("connection aborted")
                || normalized.contains("established connection was aborted");
    }

    private String summarizeCause(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return root.getClass().getSimpleName() + ": " + message;
    }
}
