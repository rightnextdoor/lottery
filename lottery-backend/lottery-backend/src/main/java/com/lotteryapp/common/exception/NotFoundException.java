package com.lotteryapp.common.exception;

import java.util.Collections;
import java.util.Map;

public class NotFoundException extends RuntimeException {

    private final String errorCode;
    private final Map<String, Object> details;

    public NotFoundException(String message) {
        this(message, "NOT_FOUND", null);
    }

    public NotFoundException(String message, Map<String, Object> details) {
        this(message, "NOT_FOUND", details);
    }

    public NotFoundException(String message, String errorCode, Map<String, Object> details) {
        super(message);
        this.errorCode = (errorCode == null || errorCode.isBlank()) ? "NOT_FOUND" : errorCode;
        this.details = (details == null) ? Collections.emptyMap() : details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
