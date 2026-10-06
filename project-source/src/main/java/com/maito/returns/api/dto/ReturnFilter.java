package com.maito.returns.api.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ReturnFilter(
    String status,
    UUID customerProfileId,
    UUID orderId
) {}