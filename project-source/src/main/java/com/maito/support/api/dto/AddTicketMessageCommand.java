package com.maito.support.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.List;

@Builder
public record AddTicketMessageCommand(
    @NotBlank(message = "Message cannot be empty")
    String message,

    List<String> attachmentUrls
) {}