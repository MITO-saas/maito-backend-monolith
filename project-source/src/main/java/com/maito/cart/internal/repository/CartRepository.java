package com.maito.cart.internal.repository;

import com.maito.cart.internal.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByCustomerProfileId(UUID customerProfileId);
    Optional<Cart> findByGuestCartId(String guestCartId);

    @Modifying
    @Query("DELETE FROM Cart c WHERE c.customerProfileId IS NULL AND c.updatedAt < :cutoff")
    int deleteInactiveGuestCartsOlderThan(@Param("cutoff") Instant cutoff);
}
