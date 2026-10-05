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

    @org.springframework.data.jpa.repository.Query("SELECT o FROM Order o WHERE o.orderStatus IN ('PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED') AND o.createdAt >= :start AND o.createdAt <= :end ORDER BY o.createdAt ASC")
    List<Order> findPaidOrdersBetween(@org.springframework.data.repository.query.Param("start") java.time.Instant start, @org.springframework.data.repository.query.Param("end") java.time.Instant end);
}
