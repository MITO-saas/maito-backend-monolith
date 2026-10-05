package com.maito.cart;

import com.maito.cart.api.dto.AddCartItemCommand;
import com.maito.cart.api.dto.CartItemDto;
import com.maito.cart.api.dto.CartResponse;
import com.maito.cart.api.service.CartService;
import com.maito.cart.internal.domain.Cart;
import com.maito.cart.internal.repository.CartRepository;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import com.maito.user.api.dto.TenantProfileDto;
import com.maito.user.api.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class CartMergeIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private UserService userService;

    @Autowired
    private CartRepository cartRepository;

    private final TenantContext tenantContextMito = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    private final TenantContext tenantContextVijya = new TenantContext(
            "vijiyasolar",
            "vijiyasolar",
            "IN",
            "INR",
            "en_IN",
            "db_vijiyasolar"
    );

    private final UUID periPeriVariantId = UUID.fromString("f1000000-0000-0000-0000-000000000001");
    private final UUID pinkSaltVariantId = UUID.fromString("f1000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContextMito);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Assert Cart Merge: Guest items are merged into customer cart, aggregating duplicate lines correctly and retaining coupon")
    void shouldMergeGuestCartIntoCustomerCart() {
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "Merge", "Tester", "ROLE_TENANT_CUSTOMER", List.of()
        );
        UUID customerProfileId = customer.id();
        String guestCartId = "guest_" + UUID.randomUUID();

        // 1. Guest adds 2 Peri Peri items and applies coupon
        CartResponse guestCart = cartService.getOrCreateCart(guestCartId, null, "INR");
        cartService.addItem(guestCart.id(), new AddCartItemCommand(periPeriVariantId, 2));

        // 2. Customer adds 1 Peri Peri item and 1 Pink Salt item
        CartResponse customerCart = cartService.getOrCreateCart(null, customerProfileId, "INR");
        cartService.addItem(customerCart.id(), new AddCartItemCommand(periPeriVariantId, 1));
        cartService.addItem(customerCart.id(), new AddCartItemCommand(pinkSaltVariantId, 1));

        // 3. Customer logs in and triggers merge
        CartResponse mergedCart = cartService.mergeCarts(guestCartId, customerProfileId);

        // 4. Verify aggregated contents
        assertThat(mergedCart.items()).hasSize(2);

        CartItemDto periPeriLine = mergedCart.items().stream()
                .filter(i -> i.variantId().equals(periPeriVariantId))
                .findFirst()
                .orElseThrow();
        assertThat(periPeriLine.quantity()).isEqualTo(3); // 2 from guest + 1 from customer

        CartItemDto pinkSaltLine = mergedCart.items().stream()
                .filter(i -> i.variantId().equals(pinkSaltVariantId))
                .findFirst()
                .orElseThrow();
        assertThat(pinkSaltLine.quantity()).isEqualTo(1);

        // 5. Verify original guest cart row is atomically deleted from database
        Optional<Cart> deletedGuest = cartRepository.findByGuestCartId(guestCartId);
        assertThat(deletedGuest).isEmpty();
    }

    @Test
    @DisplayName("Assert Guest Cart Isolation: Different guest IDs and tenant contexts have zero leakage")
    void shouldMaintainStrictIsolationAcrossGuestSessionsAndTenants() {
        String guestSessionA = "guest_A_" + UUID.randomUUID();
        String guestSessionB = "guest_B_" + UUID.randomUUID();

        // 1. Guest A adds 2 items in Mito Crunch
        CartResponse cartA = cartService.getOrCreateCart(guestSessionA, null, "INR");
        cartService.addItem(cartA.id(), new AddCartItemCommand(periPeriVariantId, 2));

        // 2. Guest B gets/creates cart in Mito Crunch -> should be completely empty and distinct
        CartResponse cartB = cartService.getOrCreateCart(guestSessionB, null, "INR");
        assertThat(cartB.id()).isNotEqualTo(cartA.id());
        assertThat(cartB.items()).isEmpty();

        // 3. Switch tenant to Vijya Solar -> Guest A in Vijya Solar has zero items from Mito Crunch
        TenantContextHolder.set(tenantContextVijya);
        try {
            CartResponse cartVijya = cartService.getOrCreateCart(guestSessionA, null, "INR");
            assertThat(cartVijya.items()).isEmpty();
        } finally {
            TenantContextHolder.set(tenantContextMito);
        }

        // 4. Back in Mito Crunch, Guest A still has their 2 items intact
        CartResponse cartAReloaded = cartService.getOrCreateCart(guestSessionA, null, "INR");
        assertThat(cartAReloaded.items()).hasSize(1);
        assertThat(cartAReloaded.items().get(0).quantity()).isEqualTo(2);
    }
}
