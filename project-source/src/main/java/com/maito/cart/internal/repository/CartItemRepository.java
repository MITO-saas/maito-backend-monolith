package com.maito.cart.internal.repository;

import com.maito.cart.internal.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findByCartId(UUID cartId);
    Optional<CartItem> findByCartIdAndVariantId(UUID cartId, UUID variantId);
    void deleteByCartId(UUID cartId);
}
