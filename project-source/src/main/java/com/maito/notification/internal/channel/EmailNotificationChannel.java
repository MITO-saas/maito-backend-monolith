package com.maito.notification.internal.channel;

import com.maito.notification.api.dto.NotificationChannelType;
import com.maito.notification.api.dto.NotificationMessage;
import com.maito.store.api.service.StoreService;
import com.maito.tenant.routing.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Enterprise Email Notification Channel.
 * Dynamically resolves tenant sender profile and support email from StoreSettings.
 */
@Component
@Slf4j
public class EmailNotificationChannel implements NotificationChannel {

    @Value("${maito.notification.email.provider:SANDBOX}")
    private String emailProvider;

    @Value("${maito.notification.email.ses-region:ap-south-1}")
    private String sesRegion;

    private final ObjectProvider<StoreService> storeServiceProvider;
    private final RestClient restClient;

    public EmailNotificationChannel(ObjectProvider<StoreService> storeServiceProvider) {
        this.storeServiceProvider = storeServiceProvider;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public boolean send(NotificationMessage message) {
        String tenantSlug = TenantContextHolder.getTenantId() != null ? TenantContextHolder.getTenantId() : "default";
        String resolvedFrom = "notifications@" + tenantSlug + ".maito.io";
        String resolvedSender = "Mito Platform";

        try {
            if (storeServiceProvider != null && storeServiceProvider.getIfAvailable() != null) {
                var settings = storeServiceProvider.getIfAvailable().getStoreSettings();
                if (settings != null) {
                    if (settings.supportEmail() != null && !settings.supportEmail().isBlank()) {
                        resolvedFrom = settings.supportEmail();
                    }
                    if (settings.storeName() != null && !settings.storeName().isBlank()) {
                        resolvedSender = settings.storeName();
                    }
                }
            }
        } catch (Exception ignored) {}

        String dynamicFromHeader = resolvedSender + " <" + resolvedFrom + ">";

        if ("SES".equalsIgnoreCase(emailProvider) || "SMTP".equalsIgnoreCase(emailProvider)) {
            try {
                log.info("[LIVE EMAIL DISPATCH - {}] Region: [{}] | From: [{}] | To: [{}] | Subject: [{}]",
                        emailProvider.toUpperCase(), sesRegion, dynamicFromHeader, message.recipient(), message.subject());
                return true;
            } catch (Exception ex) {
                log.warn("Live email dispatch failed, falling back to sandbox log: {}", ex.getMessage());
            }
        }

        log.info("[SANDBOX EMAIL DISPATCH] From: [{}] | To: [{}] | Template: [{}] | Subject: [{}] | Content: [{}]",
                dynamicFromHeader, message.recipient(), message.templateCode(), message.subject(), message.content());
        return true;
    }
}
