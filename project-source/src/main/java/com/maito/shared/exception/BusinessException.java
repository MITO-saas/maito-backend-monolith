package com.maito.shared.exception;

import lombok.Getter;

/**
 * Base unchecked exception for all business domain errors.
 */
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object details;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(ErrorCode errorCode, String message, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
}
