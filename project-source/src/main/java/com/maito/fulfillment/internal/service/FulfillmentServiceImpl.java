package com.maito.fulfillment.internal.service;

import com.maito.fulfillment.api.dto.CarrierConfigResponse;
import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.fulfillment.api.dto.CheckpointDto;
import com.maito.fulfillment.api.dto.ConfigureCarrierCommand;
import com.maito.fulfillment.api.dto.CreateShipmentCommand;
import com.maito.fulfillment.api.dto.ShipmentResponse;
import com.maito.fulfillment.api.dto.ShipmentStatus;
import com.maito.fulfillment.api.dto.TrackingTimelineResponse;
import com.maito.fulfillment.api.dto.UpdateShipmentStatusCommand;
import com.maito.fulfillment.api.service.FulfillmentService;
import com.maito.fulfillment.internal.calculator.VolumetricWeightCalculator;
import com.maito.fulfillment.internal.carrier.CarrierAdapter;
import com.maito.fulfillment.internal.carrier.CarrierAdapterFactory;
import com.maito.fulfillment.internal.carrier.ShipmentBookingRequest;
import com.maito.fulfillment.internal.carrier.ShipmentBookingResult;
import com.maito.fulfillment.internal.domain.CarrierConfiguration;
import com.maito.fulfillment.internal.domain.Shipment;
import com.maito.fulfillment.internal.domain.ShipmentCheckpoint;
import com.maito.fulfillment.internal.repository.CarrierConfigurationRepository;
import com.maito.fulfillment.internal.repository.ShipmentCheckpointRepository;
import com.maito.fulfillment.internal.repository.ShipmentRepository;
import com.maito.order.api.dto.OrderResponse;
import com.maito.order.api.service.OrderService;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FulfillmentServiceImpl implements FulfillmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentCheckpointRepository checkpointRepository;
    private final CarrierConfigurationRepository carrierConfigRepository;
    private final CarrierAdapterFactory carrierAdapterFactory;
    private final OrderService orderService;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional
    public ShipmentResponse createShipment(CreateShipmentCommand cmd) {
        OrderResponse order = orderService.getOrderById(cmd.orderId());

        // Validate order status: must be PAID or PROCESSING
        if (!"PAID".equalsIgnoreCase(order.orderStatus()) && !"PROCESSING".equalsIgnoreCase(order.orderStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Order must be in PAID or PROCESSING status to book shipment. Current status: " + order.orderStatus());
        }

        // Verify no active shipment already exists
        List<Shipment> existingShipments = shipmentRepository.findByOrderId(cmd.orderId());
        boolean hasActive = existingShipments.stream()
                .anyMatch(s -> s.getStatus() != ShipmentStatus.CANCELLED && s.getStatus() != ShipmentStatus.RTO);
        if (hasActive) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "An active shipment already exists for order: " + order.orderNumber());
        }

        // Validate carrier configuration
        CarrierConfiguration carrierConfig = carrierConfigRepository.findByCarrierType(cmd.carrierType())
                .orElseGet(() -> CarrierConfiguration.builder()
                        .carrierType(cmd.carrierType())
                        .displayName(cmd.carrierType().name())
                        .isEnabled(true)
                        .credentials(Map.of())
                        .settings(Map.of())
                        .build());

        if (Boolean.FALSE.equals(carrierConfig.getIsEnabled())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Carrier " + cmd.carrierType() + " is currently disabled by administrator.");
        }

        // Volumetric and billable weight calculation
        int deadWeightGrams = cmd.totalWeightGrams() != null ? cmd.totalWeightGrams() : 500;
        int volumetricGrams;
        if (cmd.lengthCm() != null && cmd.widthCm() != null && cmd.heightCm() != null) {
            volumetricGrams = VolumetricWeightCalculator.calculateVolumetricWeightGrams(cmd.lengthCm(), cmd.widthCm(), cmd.heightCm());
        } else if (cmd.volumetricWeightGrams() != null) {
            volumetricGrams = cmd.volumetricWeightGrams();
        } else {
            volumetricGrams = 500;
        }

        String shipmentNumber = "SHP-" + System.currentTimeMillis() + "-" + (1000 + RANDOM.nextInt(9000));
        CarrierAdapter adapter = carrierAdapterFactory.getAdapter(cmd.carrierType());

        ShipmentBookingRequest bookingRequest = new ShipmentBookingRequest(
                order.id(),
                order.orderNumber(),
                shipmentNumber,
                "Valued Customer",
                "9876543210",
                order.shippingAddressSnapshot(),
                deadWeightGrams,
                volumetricGrams,
                carrierConfig.getSettings(),
                carrierConfig.getCredentials(),
                cmd.assignedRiderName(),
                cmd.assignedRiderPhone()
        );

        ShipmentBookingResult bookingResult = adapter.bookShipment(bookingRequest);

        Shipment shipment = Shipment.builder()
                .orderId(order.id())
                .shipmentNumber(shipmentNumber)
                .carrierType(cmd.carrierType())
                .trackingNumber(bookingResult.trackingNumber())
                .status(ShipmentStatus.MANIFESTED)
                .assignedRiderName(cmd.assignedRiderName())
                .assignedRiderPhone(cmd.assignedRiderPhone())
                .deliveryOtp(bookingResult.deliveryOtp())
                .totalWeightGrams(bookingRequest.totalWeightGrams())
                .volumetricWeightGrams(bookingRequest.volumetricWeightGrams())
                .shippingLabelUrl(bookingResult.shippingLabelUrl())
                .build();

        Shipment savedShipment = shipmentRepository.save(shipment);

        // Add initial checkpoint
        ShipmentCheckpoint initialCheckpoint = ShipmentCheckpoint.builder()
                .shipmentId(savedShipment.getId())
                .checkpointStatus("MANIFESTED")
                .locationHub(bookingResult.initialCheckpointHub() != null ? bookingResult.initialCheckpointHub() : "ORIGIN_HUB")
                .statusDescription(bookingResult.initialCheckpointDesc() != null ? bookingResult.initialCheckpointDesc() : "Manifest generated.")
                .eventTimestamp(Instant.now())
                .build();

        checkpointRepository.save(initialCheckpoint);

        // Update order status to PROCESSING
        orderService.updateOrderStatus(order.id(), "PROCESSING");

        log.info("Shipment created: id=[{}] num=[{}] carrier=[{}] tracking=[{}]",
                savedShipment.getId(), savedShipment.getShipmentNumber(), savedShipment.getCarrierType(), savedShipment.getTrackingNumber());

        return toShipmentResponse(savedShipment, order.orderNumber());
    }

    @Override
    @Transactional
    public ShipmentResponse dispatchShipment(UUID shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Shipment not found: " + shipmentId));

        shipment.setStatus(ShipmentStatus.DISPATCHED);
        shipment.setDispatchedAt(Instant.now());
        Shipment savedShipment = shipmentRepository.save(shipment);

        orderService.updateOrderStatus(shipment.getOrderId(), "SHIPPED");

        ShipmentCheckpoint checkpoint = ShipmentCheckpoint.builder()
                .shipmentId(savedShipment.getId())
                .checkpointStatus("DISPATCHED")
                .locationHub("ORIGIN_HUB")
                .statusDescription("Shipment dispatched from fulfillment center")
                .eventTimestamp(Instant.now())
                .build();
        checkpointRepository.save(checkpoint);

        OrderResponse order = orderService.getOrderById(shipment.getOrderId());
        log.info("Shipment [{}] dispatched for order [{}]", savedShipment.getShipmentNumber(), order.orderNumber());
        return toShipmentResponse(savedShipment, order.orderNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public TrackingTimelineResponse getTrackingByOrderNumber(String orderNumber) {
        OrderResponse order = orderService.getOrderByNumber(orderNumber, null);

        List<Shipment> shipments = shipmentRepository.findByOrderId(order.id());
        if (shipments.isEmpty()) {
            return new TrackingTimelineResponse(
                    order.orderNumber(),
                    "PENDING_DISPATCH",
                    CarrierType.SELF_FLEET,
                    "Mito Express Logistics",
                    "AWB-PENDING",
                    ShipmentStatus.MANIFESTED,
                    "PROCESSING",
                    TrackingTimelineResponse.STANDARD_STEPPER_STAGES,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(new CheckpointDto(
                            UUID.randomUUID(),
                            "ORDER_PLACED",
                            "MITHILA_CENTRAL_HUB",
                            "Order placed and confirmed. Warehouse packing in progress.",
                            order.createdAt() != null ? order.createdAt() : Instant.now()
                    ))
            );
        }

        Shipment shipment = shipments.get(shipments.size() - 1);
        List<ShipmentCheckpoint> checkpoints = checkpointRepository.findByShipmentIdOrderByEventTimestampAsc(shipment.getId());

        List<CheckpointDto> checkpointDtos = checkpoints.stream()
                .map(cp -> new CheckpointDto(cp.getId(), cp.getCheckpointStatus(), cp.getLocationHub(), cp.getStatusDescription(), cp.getEventTimestamp()))
                .toList();

        return buildTrackingTimeline(order, shipment, checkpointDtos);
    }

    @Override
    @Transactional(readOnly = true)
    public TrackingTimelineResponse getTrackingByTrackingNumber(String trackingNumber) {
        Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "No shipment found with tracking number: " + trackingNumber));

        OrderResponse order = orderService.getOrderById(shipment.getOrderId());
        List<ShipmentCheckpoint> checkpoints = checkpointRepository.findByShipmentIdOrderByEventTimestampAsc(shipment.getId());

        List<CheckpointDto> checkpointDtos = checkpoints.stream()
                .map(cp -> new CheckpointDto(cp.getId(), cp.getCheckpointStatus(), cp.getLocationHub(), cp.getStatusDescription(), cp.getEventTimestamp()))
                .toList();

        return buildTrackingTimeline(order, shipment, checkpointDtos);
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentById(UUID shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Shipment not found: " + shipmentId));
        OrderResponse order = orderService.getOrderById(shipment.getOrderId());
        return toShipmentResponse(shipment, order.orderNumber());
    }

    @Override
    @Transactional
    public ShipmentResponse updateShipmentStatus(UUID shipmentId, UpdateShipmentStatusCommand cmd) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Shipment not found: " + shipmentId));

        OrderResponse order = orderService.getOrderById(shipment.getOrderId());

        if (cmd.newStatus() == ShipmentStatus.DELIVERED) {
            // Verify delivery OTP for SELF_FLEET
            if (shipment.getCarrierType() == CarrierType.SELF_FLEET && shipment.getDeliveryOtp() != null) {
                if (cmd.deliveryOtp() == null || !cmd.deliveryOtp().trim().equals(shipment.getDeliveryOtp())) {
                    throw new BusinessException(ErrorCode.INVALID_DELIVERY_OTP, "Invalid delivery verification OTP");
                }
            }
            shipment.setDeliveredAt(Instant.now());
            orderService.updateOrderStatus(shipment.getOrderId(), "DELIVERED");
        } else if (cmd.newStatus() == ShipmentStatus.DISPATCHED || cmd.newStatus() == ShipmentStatus.IN_TRANSIT) {
            if (shipment.getDispatchedAt() == null) {
                shipment.setDispatchedAt(Instant.now());
            }
            orderService.updateOrderStatus(shipment.getOrderId(), "SHIPPED");
        } else if (cmd.newStatus() == ShipmentStatus.CANCELLED) {
            orderService.updateOrderStatus(shipment.getOrderId(), "CANCELLED");
        }

        shipment.setStatus(cmd.newStatus());
        Shipment savedShipment = shipmentRepository.save(shipment);

        // Record checkpoint
        ShipmentCheckpoint checkpoint = ShipmentCheckpoint.builder()
                .shipmentId(savedShipment.getId())
                .checkpointStatus(cmd.newStatus().name())
                .locationHub(cmd.locationHub() != null ? cmd.locationHub() : "TRANSIT_FACILITY")
                .statusDescription(cmd.statusDescription() != null ? cmd.statusDescription() : ("Shipment transitioned to " + cmd.newStatus()))
                .eventTimestamp(Instant.now())
                .build();
        checkpointRepository.save(checkpoint);

        log.info("Shipment [{}] status updated to [{}]", savedShipment.getShipmentNumber(), cmd.newStatus());
        return toShipmentResponse(savedShipment, order.orderNumber());
    }

    @Override
    @Transactional
    public ShipmentResponse cancelShipment(UUID shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Shipment not found: " + shipmentId));

        CarrierAdapter adapter = carrierAdapterFactory.getAdapter(shipment.getCarrierType());
        adapter.cancelShipment(shipment.getTrackingNumber());

        shipment.setStatus(ShipmentStatus.CANCELLED);
        Shipment savedShipment = shipmentRepository.save(shipment);

        ShipmentCheckpoint checkpoint = ShipmentCheckpoint.builder()
                .shipmentId(savedShipment.getId())
                .checkpointStatus("CANCELLED")
                .locationHub("DISPATCH_CONTROL")
                .statusDescription("Shipment cancelled by administrator")
                .eventTimestamp(Instant.now())
                .build();
        checkpointRepository.save(checkpoint);

        OrderResponse order = orderService.getOrderById(shipment.getOrderId());
        return toShipmentResponse(savedShipment, order.orderNumber());
    }

    @Override
    @Transactional
    public CarrierConfigResponse configureCarrier(CarrierType carrierType, ConfigureCarrierCommand cmd) {
        CarrierConfiguration config = carrierConfigRepository.findByCarrierType(carrierType)
                .orElseGet(() -> CarrierConfiguration.builder()
                        .carrierType(carrierType)
                        .displayName(carrierType.name())
                        .isEnabled(true)
                        .build());

        if (cmd.isEnabled() != null) {
            config.setIsEnabled(cmd.isEnabled());
        }
        if (cmd.credentials() != null) {
            config.setCredentials(cmd.credentials());
        }
        if (cmd.settings() != null) {
            config.setSettings(cmd.settings());
        }

        CarrierConfiguration saved = carrierConfigRepository.save(config);
        return new CarrierConfigResponse(
                saved.getId(),
                saved.getCarrierType(),
                saved.getDisplayName(),
                saved.getIsEnabled(),
                saved.getSettings(),
                saved.getUpdatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarrierConfigResponse> getCarrierConfigurations() {
        return carrierConfigRepository.findAll().stream()
                .map(c -> new CarrierConfigResponse(
                        c.getId(),
                        c.getCarrierType(),
                        c.getDisplayName(),
                        c.getIsEnabled(),
                        c.getSettings(),
                        c.getUpdatedAt()
                ))
                .toList();
    }

    private TrackingTimelineResponse buildTrackingTimeline(OrderResponse order, Shipment shipment, List<CheckpointDto> checkpoints) {
        String progressStep;
        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            progressStep = "DELIVERED";
        } else if (shipment.getStatus() == ShipmentStatus.OUT_FOR_DELIVERY) {
            progressStep = "OUT_FOR_DELIVERY";
        } else if (shipment.getStatus() == ShipmentStatus.DISPATCHED || shipment.getStatus() == ShipmentStatus.IN_TRANSIT) {
            progressStep = "DISPATCHED";
        } else if (shipment.getStatus() == ShipmentStatus.MANIFESTED) {
            progressStep = "PROCESSING";
        } else {
            progressStep = "ORDER_PLACED";
        }

        String displayName = carrierConfigRepository.findByCarrierType(shipment.getCarrierType())
                .map(CarrierConfiguration::getDisplayName)
                .orElse(shipment.getCarrierType().name());

        return new TrackingTimelineResponse(
                order.orderNumber(),
                shipment.getShipmentNumber(),
                shipment.getCarrierType(),
                displayName,
                shipment.getTrackingNumber(),
                shipment.getStatus(),
                progressStep,
                TrackingTimelineResponse.STANDARD_STEPPER_STAGES,
                shipment.getAssignedRiderName(),
                shipment.getAssignedRiderPhone(),
                shipment.getShippingLabelUrl(),
                shipment.getDispatchedAt(),
                shipment.getDeliveredAt(),
                checkpoints
        );
    }

    private ShipmentResponse toShipmentResponse(Shipment s, String orderNumber) {
        return new ShipmentResponse(
                s.getId(),
                s.getOrderId(),
                orderNumber,
                s.getShipmentNumber(),
                s.getCarrierType(),
                s.getTrackingNumber(),
                s.getStatus(),
                s.getAssignedRiderName(),
                s.getAssignedRiderPhone(),
                s.getDeliveryOtp(),
                s.getTotalWeightGrams(),
                s.getVolumetricWeightGrams(),
                s.getShippingLabelUrl(),
                s.getDispatchedAt(),
                s.getDeliveredAt(),
                s.getCreatedAt()
        );
    }
}
