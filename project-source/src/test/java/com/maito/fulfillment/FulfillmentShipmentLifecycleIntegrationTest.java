package com.maito.fulfillment;

import com.maito.cart.api.dto.AddCartItemCommand;
import com.maito.cart.api.dto.CartResponse;
import com.maito.cart.api.service.CartService;
import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.fulfillment.api.dto.CreateShipmentCommand;
import com.maito.fulfillment.api.dto.ShipmentResponse;
import com.maito.fulfillment.api.dto.ShipmentStatus;
import com.maito.fulfillment.api.dto.TrackingTimelineResponse;
import com.maito.fulfillment.api.dto.UpdateShipmentStatusCommand;
import com.maito.fulfillment.api.service.FulfillmentService;
import com.maito.order.api.dto.CreateOrderCommand;
import com.maito.order.api.dto.OrderResponse;
import com.maito.order.api.dto.PaymentCallbackCommand;
import com.maito.order.api.service.OrderService;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
class FulfillmentShipmentLifecycleIntegrationTest {

    @Autowired
    private FulfillmentService fulfillmentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserService userService;

    private final TenantContext tenantContext = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    private final UUID periPeriVariantId = UUID.fromString("f1000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContext);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private OrderResponse createUnpaidOrder() {
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "Fulfillment", "Customer", "ROLE_TENANT_CUSTOMER", List.of()
        );
        CartResponse cart = cartService.getOrCreateCart(null, customer.id(), "INR");
        cartService.addItem(cart.id(), new AddCartItemCommand(periPeriVariantId, 2));

        return orderService.createOrderFromCart(cart.id(), customer.id(), new CreateOrderCommand(
                Map.of("line1", "Boring Road", "city", "Patna", "state", "Bihar", "pincode", "800001"),
                null
        ));
    }

    private OrderResponse createAndPayOrder() {
        OrderResponse unpaid = createUnpaidOrder();
        PaymentCallbackCommand payCmd = new PaymentCallbackCommand("TXN-" + UUID.randomUUID().toString().substring(0, 8), "PAID", "mock-sig");
        return orderService.confirmPayment(unpaid.id(), payCmd);
    }

    @Test
    @DisplayName("Assert creating a shipment fails fast if order is not in PAID status")
    void shouldRequirePaidOrderForShipmentBooking() {
        OrderResponse unpaidOrder = createUnpaidOrder();

        CreateShipmentCommand bookCmd = new CreateShipmentCommand(
                unpaidOrder.id(),
                CarrierType.SELF_FLEET,
                "Ramesh Singh",
                "+91 98765 11223",
                400,
                400
        );

        assertThatThrownBy(() -> fulfillmentService.createShipment(bookCmd))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    @DisplayName("Assert Self-Delivery lifecycle: Manifest -> Dispatch -> Out for delivery -> OTP Verification -> Delivered")
    void shouldExecuteSelfDeliveryLifecycleWithOtp() {
        OrderResponse paidOrder = createAndPayOrder();

        // 1. Admin books shipment with SELF_FLEET
        CreateShipmentCommand bookCmd = new CreateShipmentCommand(
                paidOrder.id(),
                CarrierType.SELF_FLEET,
                "Ramesh Singh",
                "+91 98765 11223",
                400,
                400,
                20.0,
                15.0,
                10.0
        );

        ShipmentResponse shipment = fulfillmentService.createShipment(bookCmd);
        assertThat(shipment).isNotNull();
        assertThat(shipment.status()).isEqualTo(ShipmentStatus.MANIFESTED);
        assertThat(shipment.trackingNumber()).startsWith("SELF-MITOCRUNCH-");
        assertThat(shipment.deliveryOtp()).isNotNull().hasSize(4);
        assertThat(shipment.assignedRiderName()).isEqualTo("Ramesh Singh");

        // Order transitioned to PROCESSING
        OrderResponse processingOrder = orderService.getOrderById(paidOrder.id());
        assertThat(processingOrder.orderStatus()).isEqualTo("PROCESSING");

        // 2. Track timeline by order number - verify public tracking stepper & carrier display name
        TrackingTimelineResponse timeline = fulfillmentService.getTrackingByOrderNumber(paidOrder.orderNumber());
        assertThat(timeline.shipmentNumber()).isEqualTo(shipment.shipmentNumber());
        assertThat(timeline.carrierDisplayName()).isEqualTo("Mito Express Self-Delivery Fleet");
        assertThat(timeline.currentProgressStep()).isEqualTo("PROCESSING");
        assertThat(timeline.progressStepperStages()).containsExactly("ORDER_PLACED", "PROCESSING", "DISPATCHED", "OUT_FOR_DELIVERY", "DELIVERED");
        assertThat(timeline.checkpoints()).hasSize(1);
        assertThat(timeline.checkpoints().get(0).checkpointStatus()).isEqualTo("MANIFESTED");

        // 3. Dispatch shipment -> triggers DISPATCHED state and synchronizes parent order to SHIPPED
        ShipmentResponse dispatchedShipment = fulfillmentService.dispatchShipment(shipment.id());
        assertThat(dispatchedShipment.status()).isEqualTo(ShipmentStatus.DISPATCHED);
        assertThat(dispatchedShipment.dispatchedAt()).isNotNull();

        OrderResponse shippedOrder = orderService.getOrderById(paidOrder.id());
        assertThat(shippedOrder.orderStatus()).isEqualTo("SHIPPED");

        // 4. Update status to OUT_FOR_DELIVERY
        UpdateShipmentStatusCommand outCmd = new UpdateShipmentStatusCommand(
                ShipmentStatus.OUT_FOR_DELIVERY,
                "PATNA_CENTRAL_HUB",
                "Out for delivery with rider Ramesh Singh",
                null
        );
        ShipmentResponse outShipment = fulfillmentService.updateShipmentStatus(shipment.id(), outCmd);
        assertThat(outShipment.status()).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);

        // 5. Attempt to mark DELIVERED with invalid OTP -> should fail fast with INVALID_DELIVERY_OTP
        UpdateShipmentStatusCommand wrongOtpCmd = new UpdateShipmentStatusCommand(
                ShipmentStatus.DELIVERED,
                "CUSTOMER_DOORSTEP",
                "Delivered",
                "9999" // Wrong OTP
        );
        assertThatThrownBy(() -> fulfillmentService.updateShipmentStatus(shipment.id(), wrongOtpCmd))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.INVALID_DELIVERY_OTP);

        // 6. Mark DELIVERED with correct OTP -> should succeed and synchronize order to DELIVERED
        UpdateShipmentStatusCommand correctOtpCmd = new UpdateShipmentStatusCommand(
                ShipmentStatus.DELIVERED,
                "CUSTOMER_DOORSTEP",
                "Delivered successfully after OTP verification",
                shipment.deliveryOtp()
        );
        ShipmentResponse deliveredShipment = fulfillmentService.updateShipmentStatus(shipment.id(), correctOtpCmd);
        assertThat(deliveredShipment.status()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(deliveredShipment.deliveredAt()).isNotNull();

        // Order transitioned to DELIVERED
        OrderResponse finalOrder = orderService.getOrderById(paidOrder.id());
        assertThat(finalOrder.orderStatus()).isEqualTo("DELIVERED");

        // 7. Verify full tracking timeline contains all checkpoints and current step is DELIVERED
        TrackingTimelineResponse finalTimeline = fulfillmentService.getTrackingByTrackingNumber(shipment.trackingNumber());
        assertThat(finalTimeline.currentStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(finalTimeline.currentProgressStep()).isEqualTo("DELIVERED");
        assertThat(finalTimeline.checkpoints()).hasSize(4); // MANIFESTED, DISPATCHED, OUT_FOR_DELIVERY, DELIVERED
    }

    @Test
    @DisplayName("Assert 3PL Delhivery shipment booking and cancellation")
    void shouldBookAndCancelDelhiveryShipment() {
        OrderResponse paidOrder = createAndPayOrder();

        CreateShipmentCommand bookCmd = new CreateShipmentCommand(
                paidOrder.id(),
                CarrierType.DELHIVERY,
                null,
                null,
                500,
                500
        );

        ShipmentResponse shipment = fulfillmentService.createShipment(bookCmd);
        assertThat(shipment.carrierType()).isEqualTo(CarrierType.DELHIVERY);
        assertThat(shipment.trackingNumber()).startsWith("DLV-");
        assertThat(shipment.shippingLabelUrl()).contains("delhivery.com");

        // Cancel shipment
        ShipmentResponse cancelled = fulfillmentService.cancelShipment(shipment.id());
        assertThat(cancelled.status()).isEqualTo(ShipmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("Assert tracking unknown order number returns RESOURCE_NOT_FOUND (404)")
    void shouldReturnNotFoundForUnknownOrderTracking() {
        assertThatThrownBy(() -> fulfillmentService.getTrackingByOrderNumber("UNKNOWN-ORDER-99999"))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND);
    }
}
