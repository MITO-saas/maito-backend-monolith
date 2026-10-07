package com.maito.auth.security;

import com.maito.notification.api.dto.NotificationChannelType;
import com.maito.notification.api.dto.NotificationMessage;
import com.maito.notification.api.service.NotificationDispatchService;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.store.api.service.StoreService;
import com.maito.tenant.routing.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class PhoneOtpService {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final NotificationDispatchService notificationDispatchService;
    private final Environment environment;
    private final ObjectProvider<StoreService> storeServiceProvider;

    private final Map<String, OtpEntry> fallbackCache = new ConcurrentHashMap<>();

    public PhoneOtpService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            NotificationDispatchService notificationDispatchService,
            Environment environment,
            ObjectProvider<StoreService> storeServiceProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.notificationDispatchService = notificationDispatchService;
        this.environment = environment;
        this.storeServiceProvider = storeServiceProvider;
    }

    public String sendOtp(String rawPhone) {
        String phone = normalizePhone(rawPhone);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String tenantId = TenantContextHolder.getTenantId() != null ? TenantContextHolder.getTenantId() : "global";
        String redisKey = "maito:tenant:" + tenantId + ":otp:" + phone;

        // 1. Store in Redis with 5-minute strict TTL
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        boolean storedInRedis = false;
        if (redis != null) {
            try {
                redis.opsForValue().set(redisKey, code, OTP_TTL);
                storedInRedis = true;
            } catch (Exception ex) {
                log.warn("Failed to store OTP in Redis: {}. Using fallback cache.", ex.getMessage());
            }
        }
        if (!storedInRedis) {
            fallbackCache.put(redisKey, new OtpEntry(code, Instant.now().plus(OTP_TTL)));
        }

        // 2. Resolve tenant name for message template
        String tenantName = "Mito Platform";
        try {
            if (storeServiceProvider != null && storeServiceProvider.getIfAvailable() != null) {
                var settings = storeServiceProvider.getIfAvailable().getStoreSettings();
                if (settings != null && settings.storeName() != null && !settings.storeName().isBlank()) {
                    tenantName = settings.storeName();
                }
            }
        } catch (Exception ignored) {}
        if ("Mito Platform".equals(tenantName) && tenantId != null && !tenantId.equals("global")) {
            tenantName = tenantId;
        }

        // 3. Dispatch real notification asynchronously
        String messageText = "Your verification code for " + tenantName + " is: " + code;
        try {
            notificationDispatchService.dispatchAsync(new NotificationMessage(
                    phone,
                    NotificationChannelType.SMS,
                    "PHONE_OTP",
                    "OTP Verification Code",
                    messageText,
                    Map.of("tenantId", tenantId, "otp", code)
            ));
        } catch (Exception ex) {
            log.warn("Failed to dispatch OTP notification: {}", ex.getMessage());
        }

        log.info("[OTP GATEWAY] Generated 6-digit OTP for phone [{}] (Tenant: {}): {} (Expires in 5m)",
                phone, tenantId, code);
        return code;
    }

    public boolean verifyOtp(String rawPhone, String code) {
        String phone = normalizePhone(rawPhone);
        if (code == null || code.isBlank()) {
            return false;
        }

        // Sandbox test OTP override (123456) ONLY if running in local/test/dev profile
        boolean isDevOrTest = environment.acceptsProfiles(Profiles.of("local", "test", "dev"));
        if (isDevOrTest && "123456".equals(code.trim()) && (phone.contains("9876543210") || phone.contains("test") || phone.length() >= 10)) {
            log.info("Sandbox test OTP (123456) matched for phone [{}]", phone);
            return true;
        }

        String tenantId = TenantContextHolder.getTenantId() != null ? TenantContextHolder.getTenantId() : "global";
        String redisKey = "maito:tenant:" + tenantId + ":otp:" + phone;

        String storedCode = null;
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            try {
                storedCode = redis.opsForValue().get(redisKey);
            } catch (Exception ex) {
                log.warn("Redis read error for OTP: {}", ex.getMessage());
            }
        }

        if (storedCode == null) {
            OtpEntry entry = fallbackCache.get(redisKey);
            if (entry != null) {
                if (Instant.now().isAfter(entry.expiresAt())) {
                    fallbackCache.remove(redisKey);
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "OTP code has expired. Please request a new one.");
                }
                storedCode = entry.code();
            }
        }

        if (storedCode == null) {
            log.warn("No active OTP found for phone [{}] under tenant [{}]", phone, tenantId);
            return false;
        }

        if (storedCode.equals(code.trim())) {
            if (redis != null) {
                try {
                    redis.delete(redisKey);
                } catch (Exception ignored) {}
            }
            fallbackCache.remove(redisKey);
            log.info("OTP verified successfully for phone [{}] under tenant [{}]", phone, tenantId);
            return true;
        }

        log.warn("Invalid OTP entered for phone [{}] under tenant [{}]", phone, tenantId);
        return false;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Phone number cannot be empty");
        }
        return phone.replaceAll("[^0-9+]", "");
    }

    private record OtpEntry(String code, Instant expiresAt) {}
}
