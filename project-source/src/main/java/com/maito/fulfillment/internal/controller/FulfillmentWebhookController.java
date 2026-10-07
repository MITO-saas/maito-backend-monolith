package com.maito.fulfillment.internal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.fulfillment.api.dto.ShipmentStatus;
import com.maito.fulfillment.internal.domain.Shipment;
import com.maito.fulfillment.internal.domain.ShipmentCheckpoint;
import com.maito.fulfillment.internal.repository.ShipmentCheckpointRepository;
import com.maito.fulfillment.internal.repository.ShipmentRepository;
import com.maito.notification.api.dto.NotificationChannelType;
import com.maito.notification.api.dto.NotificationMessage;
import com.maito.notification.api.service.NotificationDispatchService;
import com.maito.order.internal.domain.Order;
import com.maito.order.internal.repository.OrderRepository;
import com.maito.shared.api.ApiResponse;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.shared.idempotency.WebhookIdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Inbound 3PL Logistics Tracking & Status Ingress Webhook Controller.
 * Handles tracking updates from Delhivery, Shiprocket, BlueDart, etc.
 * Features cryptographic verification, idempotency deduplication, and automated checkpoint recording.
 */
@RestController
@RequestMapping("/api/v1/fulfillment/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Logistics Webhooks", description = "Inbound 3PL fulfillment tracking webhooks and automated status transitions")
public class FulfillmentWebhookController {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentCheckpointRepository checkpointRepository;
    private final OrderRepository orderRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final WebhookIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Value("${maito.fulfillment.webhook-secret:whsec_fulfillment_default_2026}")
    private String webhookSecret;

    @PostMapping("/{carrier}")
    @Operation(summary = "Inbound 3PL carrier tracking webhook ingress")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleCarrierWebhook(
            @PathVariable String carrier,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signatureHeader,
            @RequestHeader(value = "X-Delhivery-Signature", required = false) String delhiverySignature,
            @RequestHeader(value = "X-Shiprocket-Token", required = false) String shiprocketToken,
            @RequestBody String payload
    ) {
        String carrierNormalized = carrier != null ? carrier.trim().toLowerCase() : "unknown";
        log.info("Received fulfillment webhook for carrier: [{}]", carrierNormalized);

        // 1. Verify Signature if present
        String effectiveSignature = signatureHeader != null ? signatureHeader : delhiverySignature;
        if (effectiveSignature != null && !effectiveSignature.isBlank()) {
            boolean valid = verifyHmacSha256(payload, effectiveSignature, webhookSecret);
            if (!valid) {
                log.warn("Invalid fulfillment webhook signature for carrier [{}]: {}", carrierNormalized, effectiveSignature);
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid carrier webhook signature");
            }
        }

        // 2. Parse Payload
        try {
            JsonNode root = objectMapper.readTree(payload);
            String trackingNumber = extractTrackingNumber(root);
            String rawStatus = extractRawStatus(root);
            String location = extractLocation(root);
            String remarks = extractRemarks(root);
            String eventId = extractEventId(root, carrierNormalized, trackingNumber, rawStatus);

            if (trackingNumber == null || trackingNumber.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Missing tracking number / waybill in carrier webhook");
            }

            // 3. Idempotency Check
            if (!idempotencyService.tryAcquire(eventId)) {
                log.info("Idempotent skip: Fulfillment webhook event [{}] already processed", eventId);
                return ResponseEntity.ok(ApiResponse.ok(Map.of(
                        "processed", true,
                        "idempotent", true,
                        "trackingNumber", trackingNumber,
                        "eventId", eventId
                )));
            }

            // 4. Map carrier status to ShipmentStatus enum
            ShipmentStatus targetStatus = mapCarrierStatus(rawStatus);

            // 5. Look up shipment and apply state transitions
            Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber).orElse(null);
            if (shipment != null) {
                shipment.setStatus(targetStatus);
                if (targetStatus == ShipmentStatus.DELIVERED) {
                    shipment.setDeliveredAt(Instant.now());
                } else if ((targetStatus == ShipmentStatus.IN_TRANSIT || targetStatus == ShipmentStatus.DISPATCHED)
                        && shipment.getDispatchedAt() == null) {
                    shipment.setDispatchedAt(Instant.now());
                }
                shipmentRepository.save(shipment);

                // 6. Record Checkpoint
                ShipmentCheckpoint checkpoint = ShipmentCheckpoint.builder()
                        .shipmentId(shipment.getId())
                        .checkpointStatus(targetStatus.name())
                        .locationHub(location != null ? location : "CENTRAL_LOGISTICS_HUB")
                        .statusDescription(remarks != null ? remarks : "Carrier event update: " + targetStatus.name())
                        .eventTimestamp(Instant.now())
                        .build();
                ShipmentCheckpoint savedCheckpoint = checkpointRepository.save(checkpoint);

                log.info("Shipment [{}] status transitioned to [{}] via carrier [{}] webhook",
                        shipment.getShipmentNumber(), targetStatus, carrierNormalized);

                // 7. Notify customer via NotificationDispatchService
                notifyCustomer(shipment, targetStatus, location);

                Map<String, Object> respMap = new java.util.HashMap<>();
                respMap.put("processed", true);
                respMap.put("carrier", carrierNormalized);
                respMap.put("trackingNumber", trackingNumber);
                respMap.put("status", targetStatus.name());
                if (savedCheckpoint != null && savedCheckpoint.getId() != null) {
                    respMap.put("checkpointId", savedCheckpoint.getId().toString());
                }

                return ResponseEntity.ok(ApiResponse.ok(respMap));
            } else {
                log.warn("Shipment not found for tracking number [{}] during carrier webhook ingestion", trackingNumber);
                return ResponseEntity.ok(ApiResponse.ok(Map.of(
                        "processed", true,
                        "carrier", carrierNormalized,
                        "trackingNumber", trackingNumber,
                        "status", targetStatus.name(),
                        "note", "SHIPMENT_NOT_FOUND_IN_DB"
                )));
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            log.error("Failed to parse carrier webhook payload: {}", ex.getMessage(), ex);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Failed to parse carrier webhook: " + ex.getMessage());
        }
    }

    private String extractTrackingNumber(JsonNode root) {
        if (root.has("waybill")) return root.get("waybill").asText();
        if (root.has("awb")) return root.get("awb").asText();
        if (root.has("trackingNumber")) return root.get("trackingNumber").asText();
        if (root.has("tracking_number")) return root.get("tracking_number").asText();
        if (root.has("Shipment") && root.get("Shipment").has("AWB")) return root.get("Shipment").get("AWB").asText();
        return null;
    }

    private String extractRawStatus(JsonNode root) {
        if (root.has("status")) return root.get("status").asText();
        if (root.has("current_status")) return root.get("current_status").asText();
        if (root.has("scan_type")) return root.get("scan_type").asText();
        if (root.has("event")) return root.get("event").asText();
        if (root.has("Shipment") && root.get("Shipment").has("Status") && root.get("Shipment").get("Status").has("Status")) {
            return root.get("Shipment").get("Status").get("Status").asText();
        }
        return "IN_TRANSIT";
    }

    private String extractLocation(JsonNode root) {
        if (root.has("location")) return root.get("location").asText();
        if (root.has("hub")) return root.get("hub").asText();
        if (root.has("city")) return root.get("city").asText();
        return "REGIONAL_SORTING_HUB";
    }

    private String extractRemarks(JsonNode root) {
        if (root.has("remarks")) return root.get("remarks").asText();
        if (root.has("instructions")) return root.get("instructions").asText();
        if (root.has("comment")) return root.get("comment").asText();
        return "Package processed at logistics facility";
    }

    private String extractEventId(JsonNode root, String carrier, String awb, String status) {
        if (root.has("eventId")) return root.get("eventId").asText();
        if (root.has("id")) return root.get("id").asText();
        return "carrier:" + carrier + ":" + awb + ":" + (status != null ? status.replaceAll("\\s+", "_").toUpperCase() : "UPDATE");
    }

    private ShipmentStatus mapCarrierStatus(String raw) {
        if (raw == null) return ShipmentStatus.IN_TRANSIT;
        String upper = raw.trim().toUpperCase();

        if (upper.contains("DELIVERED") || upper.contains("DLV") || upper.equals("CLOSED")) {
            return ShipmentStatus.DELIVERED;
        } else if (upper.contains("OUT_FOR_DELIVERY") || upper.contains("OUT FOR DELIVERY") || upper.contains("OFD")) {
            return ShipmentStatus.OUT_FOR_DELIVERY;
        } else if (upper.contains("RTO") || upper.contains("RETURN") || upper.contains("UNDELIVERED") || upper.contains("REJECTED")) {
            return ShipmentStatus.RTO;
        } else if (upper.contains("MANIFEST") || upper.contains("BOOKED") || upper.contains("PICKUP_PENDING")) {
            return ShipmentStatus.MANIFESTED;
        } else {
            return ShipmentStatus.IN_TRANSIT;
        }
    }

    private void notifyCustomer(Shipment shipment, ShipmentStatus status, String location) {
        try {
            Order order = orderRepository.findById(shipment.getOrderId()).orElse(null);
            String recipient = "+919876543210";
            if (order != null && order.getShippingAddressSnapshot() != null) {
                Object phone = order.getShippingAddressSnapshot().get("phone");
                if (phone != null && !phone.toString().isBlank()) {
                    recipient = phone.toString();
                }
            }

            notificationDispatchService.dispatchAsync(new NotificationMessage(
                    recipient,
                    NotificationChannelType.WHATSAPP,
                    "SHIPMENT_TRACKING_UPDATE",
                    "Shipment Update for " + shipment.getShipmentNumber(),
                    "Your shipment (" + shipment.getTrackingNumber() + ") is now " + status.name() + " at " + location + ".",
                    Map.of(
                            "shipmentNumber", shipment.getShipmentNumber(),
                            "trackingNumber", shipment.getTrackingNumber(),
                            "status", status.name(),
                            "location", location
                    )
            ));
        } catch (Exception ex) {
            log.warn("Customer notification suppressed during carrier webhook ingestion: {}", ex.getMessage());
        }
    }

    private boolean verifyHmacSha256(String payload, String signature, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hmacBytes = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculated = HexFormat.of().formatHex(hmacBytes);
            return MessageDigest.isEqual(calculated.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
