package com.maito.b2b.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record CreateB2BBulkOrderCommand(
        Map<String, Object> shippingAddress,
        Map<String, Object> billingAddress,

        @NotEmpty(message = "Bulk order items cannot be empty")
        List<B2BBulkOrderItemCommand> items,

        @NotNull(message = "Payment terms must be specified (PREPAID, NET_30, NET_60)")
        String paymentTerms,

        String notes
) {}
