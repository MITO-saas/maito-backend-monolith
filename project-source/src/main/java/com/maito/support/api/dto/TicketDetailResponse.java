package com.maito.support.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record TicketDetailResponse(
    UUID id,
    String ticketNumber,
    UUID customerProfileId,
    UUID orderId,
    String category,
    String subject,
    String priority,
    String status,
    Instant createdAt,
    Instant updatedAt,
    List<TicketMessageDto> messages
) {}