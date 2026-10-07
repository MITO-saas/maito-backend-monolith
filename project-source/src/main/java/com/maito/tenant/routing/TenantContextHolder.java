package com.maito.tenant.routing;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Thread-safe context holder for currently executing tenant request.
 * Backed by Alibaba TransmittableThreadLocal for seamless propagation across async execution threads.
 */
@Slf4j
public final class TenantContextHolder {

    private static final TransmittableThreadLocal<TenantContext> CURRENT_CONTEXT = new TransmittableThreadLocal<>();

    private TenantContextHolder() {
        // Utility class
    }

    public static void set(TenantContext context) {
        if (context == null) {
            clear();
        } else {
            CURRENT_CONTEXT.set(context);
            log.trace("Bound tenant context: [{}]", context.tenantId());
        }
    }

    public static TenantContext get() {
        return CURRENT_CONTEXT.get();
    }

    public static void setTenantId(String tenantId) {
        if (tenantId == null) {
            clear();
        } else {
            String clean = tenantId.trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
            if ("mito_crunch".equalsIgnoreCase(clean)) {
                clean = "mitocrunch";
            } else if ("vijiya_solar".equalsIgnoreCase(clean)) {
                clean = "vijiyasolar";
            }
            String db = "db_" + clean;
            set(new TenantContext(tenantId, tenantId, "IN", "INR", "en_IN", db));
        }
    }

    public static String getTenantId() {
        TenantContext ctx = CURRENT_CONTEXT.get();
        return (ctx != null) ? ctx.tenantId() : null;
    }

    public static void clear() {
        CURRENT_CONTEXT.remove();
        log.trace("Evicted tenant context from current thread.");
    }

    public static <T> T withTenant(TenantContext context, Supplier<T> supplier) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        TenantContext previous = get();
        try {
            set(context);
            return supplier.get();
        } finally {
            if (previous != null) {
                set(previous);
            } else {
                clear();
            }
        }
    }

    public static void withTenant(TenantContext context, Runnable runnable) {
        withTenant(context, () -> {
            runnable.run();
            return null;
        });
    }
}