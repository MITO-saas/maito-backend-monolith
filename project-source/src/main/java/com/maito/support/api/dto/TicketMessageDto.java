package com.maito.support.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record TicketMessageDto(
    UUID id,
    UUID ticketId,
    UUID senderProfileId,
    String senderRole,
    String message,
    List<String> attachmentUrls,
    Instant createdAt
) {}