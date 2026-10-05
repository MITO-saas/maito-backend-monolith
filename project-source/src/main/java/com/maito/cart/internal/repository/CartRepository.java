package com.maito.cart.internal.repository;

import com.maito.cart.internal.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByCustomerProfileId(UUID customerProfileId);
    Optional<Cart> findByGuestCartId(String guestCartId);
}
