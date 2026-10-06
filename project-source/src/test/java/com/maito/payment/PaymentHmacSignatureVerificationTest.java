package com.maito.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.payment.api.service.PaymentGatewayService;
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
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PaymentHmacSignatureVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentGatewayService paymentGatewayService;

    @Autowired
    private ObjectMapper objectMapper;

    private final String secret = "secret_mock_test_2026";
    private final String webhookSecret = "rzp_webhook_secret_default_2026";

    @Test
    @DisplayName("Assert valid Razorpay HMAC-SHA256 signature is verified and tampered signature rejected")
    void testRazorpayHmacSignatureVerification() throws Exception {
        String orderId = "order_rzp_mock_123456";
        String paymentId = "pay_mock_789101";

        // Compute valid signature
        String data = orderId + "|" + paymentId;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String validSignature = HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));

        // Verification must pass
        boolean isValid = paymentGatewayService.verifyRazorpaySignature(orderId, paymentId, validSignature, secret);
        assertThat(isValid).isTrue();

        // Tampered signature must fail
        String tamperedSignature = validSignature.substring(0, validSignature.length() - 2) + "ff";
        boolean isTamperedValid = paymentGatewayService.verifyRazorpaySignature(orderId, paymentId, tamperedSignature, secret);
        assertThat(isTamperedValid).isFalse();
    }

    @Test
    @DisplayName("Assert Webhook returns HTTP 400 when HMAC signature is invalid")
    void testWebhookRejectsTamperedSignature() throws Exception {
        String payload = "{\"event\":\"payment.captured\",\"order_id\":\"order_rzp_test\",\"payment_id\":\"pay_123\"}";
        String invalidSignature = "deadbeefcafebabe1234567890abcdef";

        mockMvc.perform(post("/api/v1/payments/webhook/razorpay")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("X-Razorpay-Signature", invalidSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PAYMENT_4001"));
    }

    @Test
    @DisplayName("Assert Webhook returns HTTP 200 when HMAC signature is valid")
    void testWebhookAcceptsValidSignature() throws Exception {
        String payload = "{\"event\":\"payment.captured\",\"order_id\":\"order_rzp_mock\",\"payment_id\":\"pay_valid_123\"}";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String validSignature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post("/api/v1/payments/webhook/razorpay")
                        .header("X-Tenant-ID", "mito_crunch")
                        .header("X-Razorpay-Signature", validSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processed").value(true));
    }
}
