package com.maito.payment.internal.repository;

import com.maito.payment.api.dto.PaymentProvider;
import com.maito.payment.internal.domain.TenantPaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantPaymentConfigRepository extends JpaRepository<TenantPaymentConfig, UUID> {
    Optional<TenantPaymentConfig> findByProviderAndIsEnabledTrue(PaymentProvider provider);
    Optional<TenantPaymentConfig> findByProvider(PaymentProvider provider);
}
