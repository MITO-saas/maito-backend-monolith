package com.maito.order.api.service;

import com.maito.order.api.dto.CreateOrderCommand;
import com.maito.order.api.dto.OrderResponse;
import com.maito.order.api.dto.PaymentCallbackCommand;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResponse createOrderFromCart(UUID cartId, UUID customerProfileId, CreateOrderCommand cmd);
    OrderResponse confirmPayment(UUID orderId, PaymentCallbackCommand cmd);
    OrderResponse updateOrderStatus(UUID orderId, String newStatus);
    void cancelOrder(UUID orderId);
    OrderResponse getOrderByNumber(String orderNumber, UUID customerProfileId);
    List<OrderResponse> getCustomerOrders(UUID customerProfileId);
    List<OrderResponse> searchOrders(String status, int page, int size);
}
