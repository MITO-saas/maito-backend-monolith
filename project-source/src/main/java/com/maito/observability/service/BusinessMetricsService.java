package com.maito.observability.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Business and domain metrics registry managing business counters and gauges:
 * - maito_orders_created_total (counter, tagged by tenant, payment_method, status)
 * - maito_gmv_revenue_total (counter, tagged by tenant, tracking total gross revenue in INR)
 * - maito_rate_limit_rejections_total (counter, tagged by tenant, route_group)
 * - maito_inventory_depletion_events (counter, tagged by tenant, sku)
 * - maito_b2b_credit_used_gauge (gauge, tracking active credit utilization)
 */
@Service
@Slf4j
public class BusinessMetricsService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicReference<Double>> b2bCreditGauges = new ConcurrentHashMap<>();

    public BusinessMetricsService(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    }

    public void recordOrderCreated(String tenant, String paymentMethod, String status, BigDecimal amount) {
        String safeTenant = normalize(tenant);
        String safePayment = (paymentMethod == null || paymentMethod.isBlank()) ? "UNKNOWN" : paymentMethod.trim().toUpperCase();
        String safeStatus = (status == null || status.isBlank()) ? "UNKNOWN" : status.trim().toUpperCase();

        meterRegistry.counter("maito_orders_created_total",
                "tenant", safeTenant,
                "payment_method", safePayment,
                "status", safeStatus).increment();

        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            meterRegistry.counter("maito_gmv_revenue_total",
                    "tenant", safeTenant).increment(amount.doubleValue());
        }
        log.info("Recorded business metric: order created for tenant [{}] amount [{}]", safeTenant, amount);
    }

    public void recordRevenue(String tenant, double amount) {
        String safeTenant = normalize(tenant);
        meterRegistry.counter("maito_gmv_revenue_total", "tenant", safeTenant).increment(amount);
    }

    public void recordRateLimitRejection(String tenant, String routeGroup) {
        String safeTenant = normalize(tenant);
        String safeRoute = (routeGroup == null || routeGroup.isBlank()) ? "GENERAL" : routeGroup.trim();
        meterRegistry.counter("maito_rate_limit_rejections_total",
                "tenant", safeTenant,
                "route_group", safeRoute).increment();
        log.warn("Recorded metric: rate limit rejection for tenant [{}] on route group [{}]", safeTenant, safeRoute);
    }

    public void recordInventoryDepletion(String tenant, String sku, int quantity) {
        String safeTenant = normalize(tenant);
        String safeSku = (sku == null || sku.isBlank()) ? "UNKNOWN" : sku.trim();
        meterRegistry.counter("maito_inventory_depletion_events",
                "tenant", safeTenant,
                "sku", safeSku).increment(quantity);
    }

    public void setB2bCreditUsed(String tenant, double creditUsed) {
        String safeTenant = normalize(tenant);
        b2bCreditGauges.computeIfAbsent(safeTenant, t -> {
            AtomicReference<Double> ref = new AtomicReference<>(0.0);
            meterRegistry.gauge("maito_b2b_credit_used_gauge", Tags.of("tenant", t), ref, AtomicReference::get);
            return ref;
        }).set(creditUsed);
    }

    public double getB2bCreditUsed(String tenant) {
        String safeTenant = normalize(tenant);
        AtomicReference<Double> ref = b2bCreditGauges.get(safeTenant);
        return ref != null ? ref.get() : 0.0;
    }

    private String normalize(String val) {
        return (val == null || val.isBlank()) ? "system" : val.trim().toLowerCase();
    }
}
