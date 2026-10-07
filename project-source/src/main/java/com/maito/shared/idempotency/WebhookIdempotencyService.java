package com.maito.shared.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Webhook Idempotency & Deduplication Service.
 * Deduplicates inbound 3rd-party webhook payloads via Redis keys (webhook:processed:{eventId})
 * with a 24-hour TTL, falling back gracefully to an in-memory TTL cache when Redis is unavailable.
 */
@Service
@Slf4j
public class WebhookIdempotencyService {

    private static final String KEY_PREFIX = "webhook:processed:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final long MEMORY_TTL_MS = 24 * 60 * 60 * 1000L;

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ConcurrentHashMap<String, Long> inMemoryCache = new ConcurrentHashMap<>();

    public WebhookIdempotencyService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * Tries to acquire processing rights for the given event ID.
     *
     * @param eventId the unique provider event identifier
     * @return true if the event has NOT been processed yet (lock acquired),
     *         false if the event is a duplicate and was already processed.
     */
    public boolean tryAcquire(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }

        String key = KEY_PREFIX + eventId.trim();

        // 1. Attempt Redis lock if available
        try {
            StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
            if (redis != null) {
                Boolean success = redis.opsForValue().setIfAbsent(key, "PROCESSED", DEFAULT_TTL);
                if (Boolean.TRUE.equals(success)) {
                    log.debug("Acquired idempotency lock in Redis for event: {}", eventId);
                    return true;
                } else {
                    log.warn("Duplicate webhook event detected in Redis: {}", eventId);
                    return false;
                }
            }
        } catch (Exception ex) {
            log.warn("Redis unavailable for webhook idempotency check, falling back to memory: {}", ex.getMessage());
        }

        // 2. In-memory fallback
        long now = System.currentTimeMillis();
        cleanExpiredMemoryEntries(now);

        Long expiresAt = inMemoryCache.putIfAbsent(key, now + MEMORY_TTL_MS);
        if (expiresAt == null || expiresAt < now) {
            if (expiresAt != null) {
                inMemoryCache.put(key, now + MEMORY_TTL_MS);
            }
            log.debug("Acquired idempotency lock in memory for event: {}", eventId);
            return true;
        } else {
            log.warn("Duplicate webhook event detected in memory cache: {}", eventId);
            return false;
        }
    }

    /**
     * Check if the event has already been processed without acquiring lock.
     */
    public boolean isProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        String key = KEY_PREFIX + eventId.trim();
        try {
            StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
            if (redis != null) {
                return Boolean.TRUE.equals(redis.hasKey(key));
            }
        } catch (Exception ex) {
            log.warn("Redis check failed: {}", ex.getMessage());
        }
        Long expiresAt = inMemoryCache.get(key);
        return expiresAt != null && expiresAt > System.currentTimeMillis();
    }

    /**
     * Evict an event from idempotency store (used in testing and rollbacks).
     */
    public void evict(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        String key = KEY_PREFIX + eventId.trim();
        try {
            StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
            if (redis != null) {
                redis.delete(key);
            }
        } catch (Exception ignored) {}
        inMemoryCache.remove(key);
    }

    private void cleanExpiredMemoryEntries(long now) {
        if (inMemoryCache.size() > 5000) {
            inMemoryCache.entrySet().removeIf(entry -> entry.getValue() < now);
        }
    }
}
