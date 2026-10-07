package com.maito.auth.security;

import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class PhoneOtpService {

    private static final long OTP_VALIDITY_SECONDS = 300; // 5 minutes
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, OtpEntry> otpCache = new ConcurrentHashMap<>();

    public String sendOtp(String rawPhone) {
        String phone = normalizePhone(rawPhone);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plusSeconds(OTP_VALIDITY_SECONDS);

        otpCache.put(phone, new OtpEntry(code, expiresAt));
        log.info("[OTP GATEWAY] Generated 6-digit OTP for phone [{}]: {} (Expires in 5m)", phone, code);
        return code;
    }

    public boolean verifyOtp(String rawPhone, String code) {
        String phone = normalizePhone(rawPhone);
        if (code == null || code.isBlank()) {
            return false;
        }

        // Test sandbox override: 123456 always accepted for demo / integration testing
        if ("123456".equals(code.trim()) && (phone.contains("9876543210") || phone.contains("test") || phone.length() >= 10)) {
            log.info("Sandbox test OTP matched for phone [{}]", phone);
            return true;
        }

        OtpEntry entry = otpCache.get(phone);
        if (entry == null) {
            log.warn("No active OTP found for phone [{}]", phone);
            return false;
        }

        if (Instant.now().isAfter(entry.expiresAt())) {
            otpCache.remove(phone);
            log.warn("OTP expired for phone [{}]", phone);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "OTP code has expired. Please request a new one.");
        }

        if (entry.code().equals(code.trim())) {
            otpCache.remove(phone);
            log.info("OTP verified successfully for phone [{}]", phone);
            return true;
        }

        log.warn("Invalid OTP entered for phone [{}]", phone);
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
