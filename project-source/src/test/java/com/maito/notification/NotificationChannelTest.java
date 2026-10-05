package com.maito.notification;

import com.maito.notification.api.dto.NotificationChannelType;
import com.maito.notification.api.dto.NotificationLogDto;
import com.maito.notification.api.dto.NotificationMessage;
import com.maito.notification.api.service.NotificationDispatchService;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class NotificationChannelTest {

    @Autowired
    private NotificationDispatchService notificationDispatchService;

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

    @Test
    @DisplayName("Assert synchronous dispatch across Email, SMS, and WhatsApp channels")
    void shouldDispatchAcrossAllChannels() {
        // 1. Email Channel
        NotificationMessage emailMsg = new NotificationMessage(
                "customer@test.com",
                NotificationChannelType.EMAIL,
                "ORDER_CONFIRMED",
                "Order MC-2026-99 Confirmed",
                "Your order has been confirmed successfully.",
                Map.of("orderNumber", "MC-2026-99", "amount", 50.00)
        );
        NotificationLogDto emailLog = notificationDispatchService.dispatchSync(emailMsg);
        assertThat(emailLog).isNotNull();
        assertThat(emailLog.channel()).isEqualTo("EMAIL");
        assertThat(emailLog.status()).isEqualTo("SENT");
        assertThat(emailLog.payloadSnapshot()).containsEntry("subject", "Order MC-2026-99 Confirmed");

        // 2. SMS Channel
        NotificationMessage smsMsg = new NotificationMessage(
                "+919876543210",
                NotificationChannelType.SMS,
                "ORDER_DISPATCHED",
                null,
                "Your order MC-2026-99 has been dispatched. Track at https://mito.io/track/MC-2026-99",
                Map.of("trackingNumber", "SELF-MITOCRUNCH-112233")
        );
        NotificationLogDto smsLog = notificationDispatchService.dispatchSync(smsMsg);
        assertThat(smsLog).isNotNull();
        assertThat(smsLog.channel()).isEqualTo("SMS");
        assertThat(smsLog.status()).isEqualTo("SENT");

        // 3. WhatsApp Channel
        NotificationMessage waMsg = new NotificationMessage(
                "+919876543210",
                NotificationChannelType.WHATSAPP,
                "DELIVERY_OTP",
                null,
                "Your Mito Express delivery OTP is 4921. Share only with rider upon arrival.",
                Map.of("otp", "4921")
        );
        NotificationLogDto waLog = notificationDispatchService.dispatchSync(waMsg);
        assertThat(waLog).isNotNull();
        assertThat(waLog.channel()).isEqualTo("WHATSAPP");
        assertThat(waLog.status()).isEqualTo("SENT");

        // 4. Verify recipient history retrieval
        List<NotificationLogDto> recipientLogs = notificationDispatchService.getNotificationLogsByRecipient("+919876543210");
        assertThat(recipientLogs).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Assert asynchronous dispatch completes and stores log in tenant database")
    void shouldDispatchAsynchronously() throws Exception {
        NotificationMessage msg = new NotificationMessage(
                "async-buyer@test.com",
                NotificationChannelType.EMAIL,
                "ORDER_CONFIRMED",
                "Async Receipt",
                "Async processing completed",
                Map.of()
        );

        CompletableFuture<NotificationLogDto> future = notificationDispatchService.dispatchAsync(msg);
        NotificationLogDto result = future.get();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("SENT");
        assertThat(result.recipient()).isEqualTo("async-buyer@test.com");
    }
}
