package com.maito.tenant.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maito.tenant.domain.GlobalTenant;
import com.maito.tenant.domain.GlobalTenantDomain;
import com.maito.tenant.repository.GlobalTenantDomainRepository;
import com.maito.tenant.repository.GlobalTenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 2-tier caching resolver: Redis (L1) -> Global Master PostgreSQL (L2).
 * Caches resolved routing metadata for 15 minutes.
 * Returns TenantResolutionResult distinguishing between ACTIVE, SUSPENDED, and NOT_FOUND states.
 */
@Service
@Slf4j
public class TenantRoutingResolver {

    private static final String CACHE_KEY_PREFIX = "tenant:routing:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    private final GlobalTenantRepository tenantRepository;
    private final GlobalTenantDomainRepository domainRepository;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;

    public TenantRoutingResolver(
            GlobalTenantRepository tenantRepository,
            GlobalTenantDomainRepository domainRepository,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.domainRepository = domainRepository;
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TenantResolutionResult resolveByTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return TenantResolutionResult.notFound("Missing tenant identifier");
        }

        String cacheKey = CACHE_KEY_PREFIX + "id:" + tenantId.trim().toLowerCase();
        Optional<CachedTenantRouting> cached = getFromCache(cacheKey);
        if (cached.isPresent()) {
            return mapToResult(cached.get());
        }

        Optional<GlobalTenant> tenantOpt = tenantRepository.findById(tenantId.trim())
                .or(() -> tenantRepository.findByTenantSlug(tenantId.trim().toLowerCase()));

        if (tenantOpt.isEmpty()) {
            log.warn("Tenant not found by ID or slug: [{}]", tenantId);
            return TenantResolutionResult.notFound(tenantId);
        }

        GlobalTenant tenant = tenantOpt.get();
        CachedTenantRouting payload = toCachedRouting(tenant);
        putToCache(cacheKey, payload);

        return mapToResult(payload);
    }

    @Transactional(readOnly = true)
    public TenantResolutionResult resolveByDomain(String domainName) {
        if (domainName == null || domainName.isBlank()) {
            return TenantResolutionResult.notFound("Missing domain name");
        }

        String cleanDomain = cleanDomain(domainName);
        String cacheKey = CACHE_KEY_PREFIX + "domain:" + cleanDomain;

        Optional<CachedTenantRouting> cached = getFromCache(cacheKey);
        if (cached.isPresent()) {
            return mapToResult(cached.get());
        }

        Optional<GlobalTenantDomain> domainOpt = domainRepository.findByDomainName(cleanDomain);
        if (domainOpt.isEmpty()) {
            log.warn("Tenant domain not registered: [{}]", cleanDomain);
            return TenantResolutionResult.notFound(cleanDomain);
        }

        TenantResolutionResult result = resolveByTenantId(domainOpt.get().getTenantId());
        if (result.isActive()) {
            TenantContext ctx = result.context();
            putToCache(cacheKey, new CachedTenantRouting(
                    ctx.tenantId(),
                    ctx.tenantSlug(),
                    ctx.region(),
                    ctx.currency(),
                    ctx.locale(),
                    ctx.databaseName(),
                    "ACTIVE"
            ));
        }
        return result;
    }

    public void cacheTenantContext(TenantContext context, String domainName) {
        if (context == null) return;
        CachedTenantRouting payload = new CachedTenantRouting(
                context.tenantId(),
                context.tenantSlug(),
                context.region(),
                context.currency(),
                context.locale(),
                context.databaseName(),
                "ACTIVE"
        );
        putToCache(CACHE_KEY_PREFIX + "id:" + context.tenantId().toLowerCase(), payload);
        if (domainName != null && !domainName.isBlank()) {
            putToCache(CACHE_KEY_PREFIX + "domain:" + cleanDomain(domainName), payload);
        }
    }

    public void evictCache(String tenantId, String domainName) {
        try {
            StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
            if (redis != null) {
                if (tenantId != null) {
                    redis.delete(CACHE_KEY_PREFIX + "id:" + tenantId.trim().toLowerCase());
                }
                if (domainName != null) {
                    redis.delete(CACHE_KEY_PREFIX + "domain:" + cleanDomain(domainName));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to evict Redis cache: {}", e.getMessage());
        }
    }

    private TenantResolutionResult mapToResult(CachedTenantRouting routing) {
        String state = routing.accountState();
        if ("SUSPENDED".equalsIgnoreCase(state) || "INACTIVE".equalsIgnoreCase(state) || "DECOMMISSIONED".equalsIgnoreCase(state)) {
            log.warn("Tenant [{}] is in restricted state: [{}]", routing.tenantId(), state);
            return TenantResolutionResult.suspended(routing.tenantId(),
                    "Tenant account is currently suspended. Please contact platform administration.");
        }

        TenantContext ctx = new TenantContext(
                routing.tenantId(),
                routing.tenantSlug(),
                routing.region(),
                routing.currency(),
                routing.locale(),
                routing.databaseName()
        );
        return TenantResolutionResult.active(ctx);
    }

    private CachedTenantRouting toCachedRouting(GlobalTenant tenant) {
        Map<String, Object> routing = tenant.getRoutingConfig();
        Map<String, Object> regional = tenant.getRegionalProfile();

        String dbName = (routing != null && routing.get("db_name") != null)
                ? String.valueOf(routing.get("db_name"))
                : "db_" + tenant.getTenantSlug();

        String region = (regional != null && regional.get("country") != null)
                ? String.valueOf(regional.get("country"))
                : "IN";

        String currency = (regional != null && regional.get("currency") != null)
                ? String.valueOf(regional.get("currency"))
                : "INR";

        String locale = (regional != null && regional.get("locale") != null)
                ? String.valueOf(regional.get("locale"))
                : "en_IN";

        return new CachedTenantRouting(
                tenant.getTenantId(),
                tenant.getTenantSlug(),
                region,
                currency,
                locale,
                dbName,
                tenant.getAccountState()
        );
    }

    private Optional<CachedTenantRouting> getFromCache(String key) {
        try {
            StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
            if (redis != null) {
                String json = redis.opsForValue().get(key);
                if (json != null && !json.isBlank()) {
                    return Optional.of(objectMapper.readValue(json, CachedTenantRouting.class));
                }
            }
        } catch (Exception e) {
            log.debug("Redis cache read skipped or unavailable for key [{}]: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    private void putToCache(String key, CachedTenantRouting value) {
        try {
            StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
            if (redis != null) {
                String json = objectMapper.writeValueAsString(value);
                redis.opsForValue().set(key, json, CACHE_TTL);
            }
        } catch (Exception e) {
            log.debug("Redis cache write skipped for key [{}]: {}", key, e.getMessage());
        }
    }

    private String cleanDomain(String domain) {
        String cleaned = domain.trim().toLowerCase();
        if (cleaned.contains(":")) {
            cleaned = cleaned.substring(0, cleaned.indexOf(":"));
        }
        return cleaned;
    }
}