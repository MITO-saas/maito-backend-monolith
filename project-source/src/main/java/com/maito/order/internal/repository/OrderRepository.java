package com.maito.order.internal.repository;

import com.maito.order.internal.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByCustomerProfileIdOrderByCreatedAtDesc(UUID customerProfileId);
    List<Order> findByOrderStatusOrderByCreatedAtDesc(String orderStatus);
    List<Order> findAllByOrderByCreatedAtDesc();
}
