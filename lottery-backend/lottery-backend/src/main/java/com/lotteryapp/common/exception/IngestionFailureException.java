package com.lotteryapp.common.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class IngestionFailureException extends RuntimeException {

    private final IngestionFailureReason reason;
    private final String errorCode;
    private final Map<String, Object> details;

    public IngestionFailureException(String message, IngestionFailureReason reason, String errorCode, Map<String, Object> details) {
        super(message);
        this.reason = (reason == null) ? IngestionFailureReason.CHECK_BACK_LATER : reason;
        this.errorCode = (errorCode == null || errorCode.isBlank()) ? this.reason.name() : errorCode;
        this.details = details;
    }

    public IngestionFailureException(String message, IngestionFailureReason reason, Map<String, Object> details) {
        this(message, reason, null, details);
    }

    public IngestionFailureException(String message, IngestionFailureReason reason) {
        this(message, reason, null, null);
    }
}
