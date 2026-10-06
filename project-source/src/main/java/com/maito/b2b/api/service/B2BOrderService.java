package com.maito.b2b.api.service;

import com.maito.b2b.api.dto.CreateB2BBulkOrderCommand;
import com.maito.order.api.dto.OrderResponse;

import java.util.UUID;

public interface B2BOrderService {
    OrderResponse createBulkOrder(UUID customerProfileId, CreateB2BBulkOrderCommand cmd);
}
