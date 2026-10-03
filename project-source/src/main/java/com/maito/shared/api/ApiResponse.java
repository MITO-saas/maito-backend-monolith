package com.maito.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Standardized API response envelope for all REST endpoints across Maito domains.
 *
 * @param <T> Response payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response envelope")
public record ApiResponse<T>(
    @Schema(description = "Operation success flag", example = "true")
    boolean success,

    @Schema(description = "Response data payload")
    T data,

    @Schema(description = "Error details, if operation failed")
    ApiError error,

    @Schema(description = "Timestamp when the response was generated")
    Instant timestamp,

    @Schema(description = "Correlation / Trace ID for distributed observability", example = "req-1728000000")
    String traceId
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now(), null);
    }

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, data, null, Instant.now(), traceId);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message, null), Instant.now(), null);
    }

    public static <T> ApiResponse<T> fail(String code, String message, Object details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details), Instant.now(), null);
    }

    public record ApiError(
        String code,
        String message,
        Object details
    ) {}
}
