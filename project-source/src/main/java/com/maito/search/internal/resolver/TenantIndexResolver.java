package com.maito.search.internal.resolver;

import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import org.springframework.stereotype.Component;

@Component("tenantIndexResolver")
public class TenantIndexResolver {

    public static final String DEFAULT_TENANT = "mito_crunch";
    public static final String PRODUCT_INDEX_SUFFIX = "_products";
    public static final String PRODUCT_INDEX_VERSION = "_v1";

    public String resolveProductIndex() {
        String tenantSlug = resolveActiveTenantSlug();
        return tenantSlug + PRODUCT_INDEX_SUFFIX;
    }

    public String resolveVersionedProductIndex() {
        return resolveProductIndex() + PRODUCT_INDEX_VERSION;
    }

    public String resolveActiveTenantSlug() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx != null) {
            if (ctx.tenantSlug() != null && !ctx.tenantSlug().isBlank()) {
                return ctx.tenantSlug().trim().toLowerCase();
            }
            if (ctx.tenantId() != null && !ctx.tenantId().isBlank()) {
                return ctx.tenantId().trim().toLowerCase();
            }
        }
        return DEFAULT_TENANT;
    }
}
