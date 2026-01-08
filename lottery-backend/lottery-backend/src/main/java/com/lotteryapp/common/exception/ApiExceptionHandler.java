package com.lotteryapp.common.exception;

import com.lotteryapp.lottery.dto.common.ErrorResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(ex.getMessage(), ex.getErrorCode(), ex.getDetails()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(ex.getMessage(), ex.getErrorCode(), ex.getDetails()));
    }

    @ExceptionHandler(IngestionFailureException.class)
    public ResponseEntity<ErrorResponse> handleIngestionFailure(IngestionFailureException ex) {
        // Map to different statuses so the frontend can branch even without reading details
        HttpStatus status = (ex.getReason() == IngestionFailureReason.CHECK_BACK_LATER)
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.UNPROCESSABLE_ENTITY;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", ex.getReason().name());
        if (ex.getDetails() != null && !ex.getDetails().isEmpty()) {
            details.putAll(ex.getDetails());
        }

        return ResponseEntity.status(status)
                .body(buildError(ex.getMessage(), ex.getErrorCode(), details));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("field", fe.getField());
            e.put("message", fe.getDefaultMessage());
            e.put("rejectedValue", fe.getRejectedValue());
            errors.add(e);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errors", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("Validation failed", "BAD_REQUEST", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("path", String.valueOf(v.getPropertyPath()));
            e.put("message", v.getMessage());
            Object invalid = v.getInvalidValue();
            if (invalid != null) e.put("invalidValue", invalid);
            errors.add(e);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errors", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("Validation failed", "BAD_REQUEST", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableJson(HttpMessageNotReadableException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("Malformed request body", "BAD_REQUEST", details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("Unexpected error", "INTERNAL_SERVER_ERROR", details));
    }

    private ErrorResponse buildError(String message, String errorCode, Map<String, Object> details) {
        return ErrorResponse.builder()
                .message(message)
                .errorCode(errorCode)
                .details((details == null || details.isEmpty()) ? null : details)
                .traceId(getOrCreateTraceId())
                .build();
    }

    private String getOrCreateTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
        }
        return traceId;
    }
}
