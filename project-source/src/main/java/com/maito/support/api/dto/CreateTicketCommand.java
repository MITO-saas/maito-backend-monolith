package com.maito.support.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record CreateTicketCommand(
    UUID orderId,

    @NotBlank(message = "Ticket category is required")
    String category,

    @NotBlank(message = "Ticket subject is required")
    String subject,

    String priority,

    @NotBlank(message = "Initial message is required")
    String message,

    List<String> attachmentUrls
) {}