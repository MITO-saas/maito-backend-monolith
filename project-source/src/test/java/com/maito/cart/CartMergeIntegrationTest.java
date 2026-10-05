package com.maito.cart;

import com.maito.cart.api.dto.AddCartItemCommand;
import com.maito.cart.api.dto.CartItemDto;
import com.maito.cart.api.dto.CartResponse;
import com.maito.cart.api.service.CartService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class CartMergeIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private UserService userService;

    private final TenantContext tenantContext = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    private final UUID periPeriVariantId = UUID.fromString("f1000000-0000-0000-0000-000000000001");
    private final UUID pinkSaltVariantId = UUID.fromString("f1000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContext);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Assert Cart Merge: Guest items are merged into customer cart, aggregating duplicate lines correctly")
    void shouldMergeGuestCartIntoCustomerCart() {
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "Merge", "Tester", "ROLE_TENANT_CUSTOMER", List.of()
        );
        UUID customerProfileId = customer.id();
        String guestCartId = "guest_" + UUID.randomUUID();

        // 1. Guest adds 2 Peri Peri items
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

        // 5. Verify guest cart is cleared
        CartResponse emptyGuest = cartService.getOrCreateCart(guestCartId, null, "INR");
        assertThat(emptyGuest.items()).isEmpty();
    }
}
