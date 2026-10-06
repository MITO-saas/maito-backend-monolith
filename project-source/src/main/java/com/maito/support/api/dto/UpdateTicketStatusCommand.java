package com.maito.support.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record UpdateTicketStatusCommand(
    @NotBlank(message = "Status cannot be empty")
    String status
) {}