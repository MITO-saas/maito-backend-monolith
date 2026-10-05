package com.maito.fulfillment.internal.controller;

import com.maito.fulfillment.api.dto.TrackingTimelineResponse;
import com.maito.fulfillment.api.service.FulfillmentService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fulfillment/track")
@RequiredArgsConstructor
@Tag(name = "Storefront Tracking", description = "Public real-time package delivery tracking timeline")
public class StorefrontTrackingController {

    private final FulfillmentService fulfillmentService;

    @GetMapping("/{orderNumber}")
    @Operation(summary = "Get real-time tracking timeline by order number")
    public ResponseEntity<ApiResponse<TrackingTimelineResponse>> trackByOrderNumber(
            @PathVariable String orderNumber
    ) {
        TrackingTimelineResponse response = fulfillmentService.getTrackingByOrderNumber(orderNumber);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/awb/{trackingNumber}")
    @Operation(summary = "Get real-time tracking timeline by AWB / tracking number")
    public ResponseEntity<ApiResponse<TrackingTimelineResponse>> trackByTrackingNumber(
            @PathVariable String trackingNumber
    ) {
        TrackingTimelineResponse response = fulfillmentService.getTrackingByTrackingNumber(trackingNumber);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
