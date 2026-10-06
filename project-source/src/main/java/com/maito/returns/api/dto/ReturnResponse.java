package com.maito.returns.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record ReturnResponse(
    UUID id,
    String returnNumber,
    UUID orderId,
    UUID customerProfileId,
    String status,
    String reasonCategory,
    String customerNotes,
    List<String> proofMediaUrls,
    String qcNotes,
    BigDecimal refundAmount,
    String refundMode,
    Instant settledAt,
    Instant createdAt,
    Instant updatedAt,
    List<ReturnItemResponseDto> items
) {}