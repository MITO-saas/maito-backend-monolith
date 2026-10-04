package com.maito.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Standard error codes mapped to HTTP status codes across the platform.
 */
@Getter
public enum ErrorCode {
    RESOURCE_NOT_FOUND("MAITO_4040", "Resource not found", HttpStatus.NOT_FOUND),
    VALIDATION_FAILED("MAITO_4001", "Request validation failed", HttpStatus.BAD_REQUEST),
    BUSINESS_RULE_VIOLATION("MAITO_4002", "Business rule violation", HttpStatus.UNPROCESSABLE_ENTITY),
    INSUFFICIENT_STOCK("MAITO_4003", "Insufficient inventory stock", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT("MAITO_4091", "Duplicate request execution detected", HttpStatus.CONFLICT),
    TENANT_SUSPENDED("TENANT_SUSPENDED", "Tenant account is currently suspended", HttpStatus.FORBIDDEN),
    PROVISIONING_FAILED("MAITO_5001", "Tenant provisioning execution failed", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_SERVER_ERROR("MAITO_5000", "An unexpected internal server error occurred", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}