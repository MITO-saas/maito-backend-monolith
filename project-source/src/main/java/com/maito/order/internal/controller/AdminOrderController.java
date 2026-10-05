package com.maito.order.internal.controller;

import com.maito.order.api.dto.OrderResponse;
import com.maito.order.api.dto.UpdateOrderStatusCommand;
import com.maito.order.api.service.OrderService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Admin Orders", description = "Tenant Admin order lifecycle and status management")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "Search orders across statuses (Admin only)")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> searchOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        List<OrderResponse> orders = orderService.searchOrders(status, page, size);
        return ResponseEntity.ok(ApiResponse.ok(orders));
    }

    @PutMapping("/{orderId}/status")
    @Operation(summary = "Update order status (Admin only)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusCommand cmd
    ) {
        OrderResponse response = orderService.updateOrderStatus(orderId, cmd.status());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
