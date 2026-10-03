package com.maito.shared.exception;

import com.maito.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler enforcing RFC 7807 compatible unified ApiResponse.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception on [{}]: {} ({})", request.getRequestURI(), ex.getMessage(), ex.getErrorCode().getCode());
        ApiResponse<Void> response = ApiResponse.fail(ex.getErrorCode().getCode(), ex.getMessage(), ex.getDetails());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Validation failure on request: {}", fieldErrors);
        ApiResponse<Void> response = ApiResponse.fail(
            ErrorCode.VALIDATION_FAILED.getCode(),
            ErrorCode.VALIDATION_FAILED.getMessage(),
            fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled internal exception on [{}]: ", request.getRequestURI(), ex);
        ApiResponse<Void> response = ApiResponse.fail(
            ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
            "An unexpected error occurred. Please contact support."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
