package com.maito.payment.internal.repository;

import com.maito.payment.internal.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByGatewayOrderId(String gatewayOrderId);
    List<PaymentTransaction> findByOrderId(UUID orderId);
}
