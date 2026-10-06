package com.maito.order.internal.controller;

import com.maito.auth.security.UserPrincipal;
import com.maito.cart.api.dto.CartResponse;
import com.maito.cart.api.service.CartService;
import com.maito.order.api.dto.CreateOrderCommand;
import com.maito.order.api.dto.OrderResponse;
import com.maito.order.api.dto.PaymentCallbackCommand;
import com.maito.order.api.service.OrderService;
import com.maito.shared.api.ApiResponse;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Storefront Checkout & Orders", description = "Order placement, payment confirmation, and customer order history")
public class StorefrontOrderController {

    private final OrderService orderService;
    private final CartService cartService;

    @PostMapping("/api/v1/checkout/create-order")
    @Operation(summary = "Checkout active cart and place order with atomic stock reservation")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestHeader(value = "X-Cart-ID", required = false) String guestCartId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateOrderCommand cmd
    ) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Customer login required to place order");
        }

        CartResponse cart = cartService.getOrCreateCart(guestCartId, principal.getProfileId(), "INR");
        OrderResponse response = orderService.createOrderFromCart(cart.id(), principal.getProfileId(), cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }


    @PostMapping("/api/v1/checkout/payment-callback")
    @Operation(summary = "Payment gateway webhook confirmation callback with body orderId")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmPaymentWithBody(
            @RequestBody java.util.Map<String, Object> body
    ) {
        String orderIdStr = (String) body.get("orderId");
        UUID orderId = UUID.fromString(orderIdStr);
        String paymentRef = (String) body.getOrDefault("paymentReference", "pay_mock_" + System.currentTimeMillis());
        String status = (String) body.getOrDefault("status", "PAID");
        String sig = (String) body.get("signature");
        PaymentCallbackCommand cmd = new PaymentCallbackCommand(paymentRef, status, sig);
        OrderResponse response = orderService.confirmPayment(orderId, cmd);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/api/v1/checkout/payment-callback/{orderId}")
    @Operation(summary = "Payment gateway webhook confirmation callback")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmPayment(
            @PathVariable UUID orderId,
            @Valid @RequestBody PaymentCallbackCommand cmd
    ) {
        OrderResponse response = orderService.confirmPayment(orderId, cmd);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/api/v1/account/orders")
    @Operation(summary = "Get list of customer past orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getCustomerOrders(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Authentication required");
        }
        List<OrderResponse> orders = orderService.getCustomerOrders(principal.getProfileId());
        return ResponseEntity.ok(ApiResponse.ok(orders));
    }

    @GetMapping("/api/v1/account/orders/{orderNumber}")
    @Operation(summary = "Get customer single order details by order number")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetails(
            @PathVariable String orderNumber,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null || principal.getProfileId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Authentication required");
        }
        OrderResponse response = orderService.getOrderByNumber(orderNumber, principal.getProfileId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}

