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
        ORDER_NOT_DELIVERED("RET_4001", "Only delivered orders are eligible for return", HttpStatus.BAD_REQUEST),
    ORDER_NOT_ELIGIBLE_FOR_RETURN("RET_4002", "Order does not belong to customer or is not eligible for return", HttpStatus.BAD_REQUEST),
    INVALID_RETURN_STATUS("RET_4003", "Invalid return request status for this operation", HttpStatus.BAD_REQUEST),
    RETURN_NOT_FOUND("RET_4040", "Return request not found", HttpStatus.NOT_FOUND),
    TICKET_NOT_FOUND("TCK_4040", "Support ticket not found", HttpStatus.NOT_FOUND),
    INVALID_TICKET_STATUS("TCK_4001", "Invalid support ticket status", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_B2B_CREDIT("B2B_4001", "Insufficient B2B credit line", HttpStatus.BAD_REQUEST),
    B2B_PARTNER_NOT_FOUND("B2B_4040", "B2B partner profile not found", HttpStatus.NOT_FOUND),
    B2B_PARTNER_NOT_VERIFIED("B2B_4002", "B2B partner is not verified for credit or wholesale orders", HttpStatus.BAD_REQUEST),
    B2B_INVOICE_NOT_FOUND("B2B_4041", "B2B invoice not found", HttpStatus.NOT_FOUND),
    B2B_INVOICE_ALREADY_PAID("B2B_4003", "B2B invoice is already paid", HttpStatus.BAD_REQUEST),
    INVALID_GSTIN("B2B_4004", "Invalid GSTIN format", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED("GATEWAY_4290", "Too many requests. Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
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
