package com.lotteryapp.common.exception;

import java.util.Collections;
import java.util.Map;

public class BadRequestException extends RuntimeException {

    private final String errorCode;
    private final Map<String, Object> details;

    public BadRequestException(String message) {
        this(message, "BAD_REQUEST", null);
    }

    public BadRequestException(String message, Map<String, Object> details) {
        this(message, "BAD_REQUEST", details);
    }

    public BadRequestException(String message, String errorCode, Map<String, Object> details) {
        super(message);
        this.errorCode = (errorCode == null || errorCode.isBlank()) ? "BAD_REQUEST" : errorCode;
        this.details = (details == null) ? Collections.emptyMap() : details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
