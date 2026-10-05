package com.maito.fulfillment.internal.controller;

import com.maito.fulfillment.api.dto.CarrierConfigResponse;
import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.fulfillment.api.dto.ConfigureCarrierCommand;
import com.maito.fulfillment.api.dto.CreateShipmentCommand;
import com.maito.fulfillment.api.dto.ShipmentResponse;
import com.maito.fulfillment.api.dto.UpdateShipmentStatusCommand;
import com.maito.fulfillment.api.service.FulfillmentService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/fulfillment")
@RequiredArgsConstructor
@Tag(name = "Admin Logistics & Fulfillment", description = "Pluggable carrier dispatch, shipment booking, and delivery OTP verification")
public class AdminFulfillmentController {

    private final FulfillmentService fulfillmentService;

    @PostMapping("/shipments")
    @Operation(summary = "Book and manifest shipment for paid order")
    public ResponseEntity<ApiResponse<ShipmentResponse>> createShipment(
            @Valid @RequestBody CreateShipmentCommand cmd
    ) {
        ShipmentResponse response = fulfillmentService.createShipment(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/shipments/{shipmentId}/dispatch")
    @Operation(summary = "Dispatch shipment and synchronize parent order status to SHIPPED")
    public ResponseEntity<ApiResponse<ShipmentResponse>> dispatchShipment(
            @PathVariable UUID shipmentId
    ) {
        ShipmentResponse response = fulfillmentService.dispatchShipment(shipmentId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/shipments/{shipmentId}")
    @Operation(summary = "Get shipment details by ID")
    public ResponseEntity<ApiResponse<ShipmentResponse>> getShipment(
            @PathVariable UUID shipmentId
    ) {
        ShipmentResponse response = fulfillmentService.getShipmentById(shipmentId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/shipments/{shipmentId}/status")
    @Operation(summary = "Update shipment status (supports delivery verification OTP for self-fleet)")
    public ResponseEntity<ApiResponse<ShipmentResponse>> updateStatus(
            @PathVariable UUID shipmentId,
            @Valid @RequestBody UpdateShipmentStatusCommand cmd
    ) {
        ShipmentResponse response = fulfillmentService.updateShipmentStatus(shipmentId, cmd);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/shipments/{shipmentId}/cancel")
    @Operation(summary = "Cancel shipment and notify carrier")
    public ResponseEntity<ApiResponse<ShipmentResponse>> cancelShipment(
            @PathVariable UUID shipmentId
    ) {
        ShipmentResponse response = fulfillmentService.cancelShipment(shipmentId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/carriers")
    @Operation(summary = "List all configured logistics carriers")
    public ResponseEntity<ApiResponse<List<CarrierConfigResponse>>> getCarriers() {
        List<CarrierConfigResponse> carriers = fulfillmentService.getCarrierConfigurations();
        return ResponseEntity.ok(ApiResponse.ok(carriers));
    }

    @PutMapping("/carriers/{carrierType}")
    @Operation(summary = "Update carrier credentials, settings, or active status")
    public ResponseEntity<ApiResponse<CarrierConfigResponse>> configureCarrier(
            @PathVariable CarrierType carrierType,
            @RequestBody ConfigureCarrierCommand cmd
    ) {
        CarrierConfigResponse response = fulfillmentService.configureCarrier(carrierType, cmd);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
