package com.maito.integrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.fulfillment.api.dto.CarrierType;
import com.maito.fulfillment.api.dto.ShipmentStatus;
import com.maito.fulfillment.internal.domain.Shipment;
import com.maito.fulfillment.internal.domain.ShipmentCheckpoint;
import com.maito.fulfillment.internal.repository.ShipmentCheckpointRepository;
import com.maito.fulfillment.internal.repository.ShipmentRepository;
import com.maito.notification.api.dto.NotificationChannelType;
import com.maito.notification.api.dto.NotificationLogDto;
import com.maito.notification.api.dto.NotificationMessage;
import com.maito.notification.api.service.NotificationDispatchService;
import com.maito.order.internal.domain.Order;
import com.maito.order.internal.repository.OrderRepository;
import com.maito.payment.api.dto.PaymentInitResponse;
import com.maito.payment.api.service.PaymentGatewayService;
import com.maito.payment.internal.domain.PaymentTransaction;
import com.maito.payment.internal.repository.PaymentTransactionRepository;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IntegrationsE2EIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentGatewayService paymentGatewayService;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ShipmentCheckpointRepository checkpointRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @Autowired
    private ObjectMapper objectMapper;

    private final String razorpayWebhookSecret = "rzp_webhook_secret_default_2026";
    private final String stripeWebhookSecret = "whsec_mock_test_stripe_2026";
    private final String fulfillmentWebhookSecret = "whsec_fulfillment_default_2026";

    private final TenantContext tenantContext = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContext);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // =========================================================================
    // 1. PAYMENT GATEWAY TESTS: STRIPE & RAZORPAY
    // =========================================================================

    @Test
    @DisplayName("Assert Stripe payment initialization and webhook signature verification")
    void testStripePaymentAndWebhookFlow() throws Exception {
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "StripeBuyer_" + System.currentTimeMillis(), "Customer", "ROLE_TENANT_CUSTOMER", List.of()
        );

        Order order = orderRepository.save(Order.builder()
                .orderNumber("ORD-STRIPE-" + System.currentTimeMillis())
                .customerProfileId(customer.id())
                .subtotalAmount(new BigDecimal("999.00"))
                .totalAmount(new BigDecimal("999.00"))
                .shippingAddressSnapshot(Map.of("phone", "+919876543210", "city", "Mumbai"))
                .build());

        // 1. Initialize Stripe Payment
        PaymentInitResponse initResp = paymentGatewayService.initializePayment(order.getId(), new BigDecimal("999.00"), "INR", "STRIPE");
        assertThat(initResp).isNotNull();
        assertThat(initResp.gatewayProvider()).isEqualTo("STRIPE");
        assertThat(initResp.gatewayOrderId()).startsWith("pi_stripe_");

        String gatewayOrderId = initResp.gatewayOrderId();
        String eventId = "evt_stripe_test_" + UUID.randomUUID();
        long now = Instant.now().getEpochSecond();

        String payload = String.format("""
                {
                  "id": "%s",
                  "type": "payment_intent.succeeded",
                  "data": {
                    "object": {
                      "id": "%s",
                      "amount": 99900,
                      "currency": "inr",
                      "metadata": {
                        "gatewayOrderId": "%s"
                      }
                    }
                  }
                }
                """, eventId, gatewayOrderId, gatewayOrderId);

        // Generate valid Stripe-Signature: t={timestamp},v1={hash}
        String signedPayload = now + "." + payload;
        String validV1 = computeHmacSha256(signedPayload, stripeWebhookSecret);
        String stripeSigHeader = "t=" + now + ",v1=" + validV1;

        // Clear thread-local context so MockMvc triggers clean multi-tenant filter resolution
        TenantContextHolder.clear();

        // 2. Post to Stripe webhook endpoint
        mockMvc.perform(post("/api/v1/payments/webhook/stripe")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Stripe-Signature", stripeSigHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processed").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        // Confirm database transaction is marked SUCCESS
        TenantContextHolder.set(tenantContext);
        PaymentTransaction tx = paymentTransactionRepository.findByGatewayOrderId(gatewayOrderId).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo("SUCCESS");

        // 3. Test Idempotency: send duplicate webhook event with same event ID
        TenantContextHolder.clear();
        mockMvc.perform(post("/api/v1/payments/webhook/stripe")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Stripe-Signature", stripeSigHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ALREADY_PROCESSED"));
    }

    @Test
    @DisplayName("Assert Stripe webhook rejects tampered signature and replay attacks")
    void testStripeSignatureSecurityAndReplayDefense() throws Exception {
        String payload = "{\"id\":\"evt_test_replay\",\"type\":\"payment_intent.succeeded\"}";

        TenantContextHolder.clear();

        // A. Tampered signature
        String tamperedHeader = "t=" + Instant.now().getEpochSecond() + ",v1=invalid_bad_sig_123456";
        mockMvc.perform(post("/api/v1/payments/webhook/stripe")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Stripe-Signature", tamperedHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PAYMENT_4001"));

        // B. Replay attack: timestamp is 15 minutes in the past (> 300s tolerance)
        long oldTimestamp = Instant.now().getEpochSecond() - 900;
        String oldSignedPayload = oldTimestamp + "." + payload;
        String oldV1 = computeHmacSha256(oldSignedPayload, stripeWebhookSecret);
        String replaySigHeader = "t=" + oldTimestamp + ",v1=" + oldV1;

        mockMvc.perform(post("/api/v1/payments/webhook/stripe")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("Stripe-Signature", replaySigHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PAYMENT_4001"));
    }

    @Test
    @DisplayName("Assert Razorpay webhook handles payment.failed and refund.processed events")
    void testRazorpayFailureAndRefundEvents() throws Exception {
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "RzpBuyer_" + System.currentTimeMillis(), "Customer", "ROLE_TENANT_CUSTOMER", List.of()
        );

        Order order = orderRepository.save(Order.builder()
                .orderNumber("ORD-RZP-" + System.currentTimeMillis())
                .customerProfileId(customer.id())
                .subtotalAmount(new BigDecimal("750.00"))
                .totalAmount(new BigDecimal("750.00"))
                .shippingAddressSnapshot(Map.of("phone", "+919876543210"))
                .build());

        PaymentInitResponse initResp = paymentGatewayService.initializePayment(order.getId(), new BigDecimal("750.00"), "INR");
        String gatewayOrderId = initResp.gatewayOrderId();

        // 1. Process payment.failed event
        String failPayload = String.format("""
                {
                  "id": "evt_rzp_fail_%s",
                  "event": "payment.failed",
                  "order_id": "%s",
                  "payment_id": "pay_fail_123"
                }
                """, UUID.randomUUID(), gatewayOrderId);
        String failSig = computeHmacSha256(failPayload, razorpayWebhookSecret);

        TenantContextHolder.clear();
        mockMvc.perform(post("/api/v1/payments/webhook/razorpay")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("X-Razorpay-Signature", failSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        TenantContextHolder.set(tenantContext);
        PaymentTransaction txFailed = paymentTransactionRepository.findByGatewayOrderId(gatewayOrderId).orElseThrow();
        assertThat(txFailed.getStatus()).isEqualTo("FAILED");

        // 2. Process refund.processed event
        String refundPayload = String.format("""
                {
                  "id": "evt_rzp_refund_%s",
                  "event": "refund.processed",
                  "order_id": "%s",
                  "payment_id": "pay_refund_123"
                }
                """, UUID.randomUUID(), gatewayOrderId);
        String refundSig = computeHmacSha256(refundPayload, razorpayWebhookSecret);

        TenantContextHolder.clear();
        mockMvc.perform(post("/api/v1/payments/webhook/razorpay")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("X-Razorpay-Signature", refundSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        TenantContextHolder.set(tenantContext);
        PaymentTransaction txRefunded = paymentTransactionRepository.findByGatewayOrderId(gatewayOrderId).orElseThrow();
        assertThat(txRefunded.getStatus()).isEqualTo("REFUNDED");
    }

    // =========================================================================
    // 2. LOGISTICS 3PL FULFILLMENT WEBHOOK TESTS
    // =========================================================================

    @Test
    @DisplayName("Assert Delhivery carrier webhook transitions shipment from IN_TRANSIT to DELIVERED and records checkpoints")
    void testCarrierWebhookStatusTransitionsAndCheckpoints() throws Exception {
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "LogisticsUser_" + System.currentTimeMillis(), "Customer", "ROLE_TENANT_CUSTOMER", List.of()
        );

        Order order = orderRepository.save(Order.builder()
                .orderNumber("ORD-LOG-" + System.currentTimeMillis())
                .customerProfileId(customer.id())
                .subtotalAmount(new BigDecimal("1200.00"))
                .totalAmount(new BigDecimal("1200.00"))
                .orderStatus("PAID")
                .paymentStatus("PAID")
                .shippingAddressSnapshot(Map.of("phone", "+919876543210", "city", "Patna"))
                .build());

        String awb = "DLV-TEST-" + System.currentTimeMillis() + "-IN";
        Shipment shipment = shipmentRepository.save(Shipment.builder()
                .orderId(order.getId())
                .shipmentNumber("SHP-" + System.currentTimeMillis())
                .carrierType(CarrierType.DELHIVERY)
                .trackingNumber(awb)
                .status(ShipmentStatus.MANIFESTED)
                .totalWeightGrams(500)
                .volumetricWeightGrams(500)
                .build());

        // Step 1: Webhook IN_TRANSIT event
        String inTransitPayload = String.format("""
                {
                  "eventId": "evt_dlv_trans_%s",
                  "waybill": "%s",
                  "status": "In Transit",
                  "location": "PATNA_SORTING_HUB",
                  "remarks": "Package arrived at regional sorting hub"
                }
                """, UUID.randomUUID(), awb);
        String inTransitSig = computeHmacSha256(inTransitPayload, fulfillmentWebhookSecret);

        TenantContextHolder.clear();
        mockMvc.perform(post("/api/v1/fulfillment/webhooks/delhivery")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("X-Delhivery-Signature", inTransitSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inTransitPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data.trackingNumber").value(awb));

        TenantContextHolder.set(tenantContext);
        Shipment updated1 = shipmentRepository.findById(shipment.getId()).orElseThrow();
        assertThat(updated1.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(updated1.getDispatchedAt()).isNotNull();

        // Step 2: Webhook OUT_FOR_DELIVERY event
        String ofdPayload = String.format("""
                {
                  "eventId": "evt_dlv_ofd_%s",
                  "waybill": "%s",
                  "status": "OUT_FOR_DELIVERY",
                  "location": "PATNA_DELIVERY_CENTER",
                  "remarks": "Assigned to courier van for final delivery"
                }
                """, UUID.randomUUID(), awb);
        String ofdSig = computeHmacSha256(ofdPayload, fulfillmentWebhookSecret);

        TenantContextHolder.clear();
        mockMvc.perform(post("/api/v1/fulfillment/webhooks/delhivery")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("X-Webhook-Signature", ofdSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ofdPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"));

        TenantContextHolder.set(tenantContext);
        Shipment updated2 = shipmentRepository.findById(shipment.getId()).orElseThrow();
        assertThat(updated2.getStatus()).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);

        // Step 3: Webhook DELIVERED event
        String deliveredPayload = String.format("""
                {
                  "eventId": "evt_dlv_dlv_%s",
                  "waybill": "%s",
                  "status": "Delivered",
                  "location": "PATNA_DOORSTEP",
                  "remarks": "Successfully handed over to customer"
                }
                """, UUID.randomUUID(), awb);
        String delSig = computeHmacSha256(deliveredPayload, fulfillmentWebhookSecret);

        TenantContextHolder.clear();
        mockMvc.perform(post("/api/v1/fulfillment/webhooks/delhivery")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("X-Delhivery-Signature", delSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deliveredPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));

        TenantContextHolder.set(tenantContext);
        Shipment finalShipment = shipmentRepository.findById(shipment.getId()).orElseThrow();
        assertThat(finalShipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(finalShipment.getDeliveredAt()).isNotNull();

        // Verify Checkpoints persisted in database
        List<ShipmentCheckpoint> checkpoints = checkpointRepository.findByShipmentIdOrderByEventTimestampAsc(shipment.getId());
        assertThat(checkpoints).hasSizeGreaterThanOrEqualTo(3);
        assertThat(checkpoints.stream().map(ShipmentCheckpoint::getCheckpointStatus).toList())
                .contains("IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED");

        // Step 4: Duplicate Webhook Idempotency Check
        TenantContextHolder.clear();
        mockMvc.perform(post("/api/v1/fulfillment/webhooks/delhivery")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("X-Delhivery-Signature", delSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deliveredPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent").value(true));
    }

    @Test
    @DisplayName("Assert Fulfillment webhook rejects invalid signature")
    void testCarrierWebhookRejectsTamperedSignature() throws Exception {
        String payload = "{\"waybill\":\"DLV-12345\",\"status\":\"Delivered\"}";
        String invalidSig = "bad_signature_deadbeef";

        TenantContextHolder.clear();
        mockMvc.perform(post("/api/v1/fulfillment/webhooks/delhivery")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("X-Webhook-Signature", invalidSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // =========================================================================
    // 3. ASYNCHRONOUS NOTIFICATION DISPATCH TEST
    // =========================================================================

    @Test
    @DisplayName("Assert async notification executes in notification-worker thread pool")
    void testAsyncNotificationExecution() throws Exception {
        NotificationMessage msg = new NotificationMessage(
                "+919876543210",
                NotificationChannelType.SMS,
                "ORDER_UPDATE",
                null,
                "Shipment dispatched successfully",
                Map.of("trackingNumber", "TEST-AWB-001")
        );

        CompletableFuture<NotificationLogDto> future = notificationDispatchService.dispatchAsync(msg);
        NotificationLogDto logDto = future.get();

        assertThat(logDto).isNotNull();
        assertThat(logDto.status()).isEqualTo("SENT");
        Object executedThread = logDto.payloadSnapshot().get("executedThread");
        assertThat(executedThread).isNotNull();
        assertThat(executedThread.toString()).startsWith("notification-worker-");
    }

    private String computeHmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
