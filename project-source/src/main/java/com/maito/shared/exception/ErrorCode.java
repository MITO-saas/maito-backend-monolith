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
    AUTHENTICATION_FAILED("AUTH_4010", "Invalid email or password", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("AUTH_4011", "Account is locked due to excessive failed login attempts", HttpStatus.FORBIDDEN),
    USER_ALREADY_EXISTS("AUTH_4090", "User with given email already exists", HttpStatus.CONFLICT),
    TOKEN_EXPIRED_OR_INVALID("AUTH_4012", "JWT token expired or invalid", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("AUTH_4030", "Access denied: insufficient privileges", HttpStatus.FORBIDDEN),
    CARRIER_NOT_SUPPORTED("FULFILLMENT_4001", "Carrier not supported or unconfigured", HttpStatus.BAD_REQUEST),
    INVALID_DELIVERY_OTP("FULFILLMENT_4002", "Invalid delivery verification OTP", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_WALLET_BALANCE("WALLET_4001", "Insufficient wallet balance", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_SIGNATURE("PAYMENT_4001", "Invalid payment gateway signature", HttpStatus.BAD_REQUEST),
    WALLET_NOT_FOUND("WALLET_4040", "Customer wallet not found", HttpStatus.NOT_FOUND),
    PAYMENT_TRANSACTION_NOT_FOUND("PAYMENT_4040", "Payment transaction not found", HttpStatus.NOT_FOUND),
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
