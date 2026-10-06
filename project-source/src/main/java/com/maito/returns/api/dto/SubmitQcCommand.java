package com.maito.returns.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record SubmitQcCommand(
    @NotNull(message = "Passed flag is required")
    Boolean passed,

    String qcNotes,

    String refundMode
) {}