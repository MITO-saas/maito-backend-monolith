package com.maito.returns.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record CreateReturnCommand(
    @NotNull(message = "Order ID is required")
    UUID orderId,

    @NotBlank(message = "Reason category is required")
    String reasonCategory,

    String customerNotes,

    List<String> proofMediaUrls,

    @NotEmpty(message = "At least one item must be returned")
    @Valid
    List<ReturnItemRequestDto> items
) {}