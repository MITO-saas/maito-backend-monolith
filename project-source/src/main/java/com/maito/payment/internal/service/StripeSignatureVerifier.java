package com.maito.payment.internal.service;

import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Enterprise Stripe Webhook Signature Verifier.
 * Validates Stripe-Signature header: t=timestamp,v1=signature.
 * Mitigates replay attacks by verifying timestamp is within 300 seconds (5 minutes) tolerance.
 */
@Component
@Slf4j
public class StripeSignatureVerifier {

    private static final long DEFAULT_TOLERANCE_SECONDS = 300L; // 5 minutes

    /**
     * Verifies Stripe signature against payload and webhook secret.
     *
     * @param payload       raw request body
     * @param sigHeader     Stripe-Signature header value (e.g. t=1614000000,v1=abc123...)
     * @param webhookSecret the Stripe webhook secret
     * @return true if valid, false otherwise
     */
    public boolean verifySignature(String payload, String sigHeader, String webhookSecret) {
        return verifySignature(payload, sigHeader, webhookSecret, DEFAULT_TOLERANCE_SECONDS);
    }

    public boolean verifySignature(String payload, String sigHeader, String webhookSecret, long toleranceSeconds) {
        if (payload == null || sigHeader == null || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }

        try {
            long timestamp = -1;
            List<String> signatures = new ArrayList<>();

            String[] items = sigHeader.split(",");
            for (String item : items) {
                String[] itemParts = item.trim().split("=", 2);
                if (itemParts.length == 2) {
                    if ("t".equalsIgnoreCase(itemParts[0])) {
                        try {
                            timestamp = Long.parseLong(itemParts[1]);
                        } catch (NumberFormatException nfe) {
                            log.warn("Invalid timestamp format in Stripe signature header: {}", itemParts[1]);
                            return false;
                        }
                    } else if ("v1".equalsIgnoreCase(itemParts[0])) {
                        signatures.add(itemParts[1]);
                    }
                }
            }

            if (timestamp <= 0 || signatures.isEmpty()) {
                log.warn("Missing timestamp or v1 signatures in Stripe-Signature header");
                return false;
            }

            // Replay attack prevention: check timestamp tolerance
            long currentEpoch = Instant.now().getEpochSecond();
            if (toleranceSeconds > 0 && Math.abs(currentEpoch - timestamp) > toleranceSeconds) {
                log.warn("Stripe webhook timestamp [{}] exceeds tolerance of [{}]s (current: [{}]) - replay attack suspected",
                        timestamp, toleranceSeconds, currentEpoch);
                throw new BusinessException(ErrorCode.INVALID_PAYMENT_SIGNATURE, "Stripe webhook timestamp out of tolerance (replay attack rejected)");
            }

            // Expected signature payload: {timestamp}.{payload}
            String signedPayload = timestamp + "." + payload;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hmacBytes = sha256Hmac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(hmacBytes);

            for (String signature : signatures) {
                if (MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                    return true;
                }
            }

            log.warn("Stripe signature mismatch against expected HMAC");
            return false;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            log.error("Failed to verify Stripe signature: {}", ex.getMessage(), ex);
            return false;
        }
    }
}
