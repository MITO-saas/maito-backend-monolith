package com.maito.cart;

import com.maito.cart.internal.domain.Cart;
import com.maito.cart.internal.repository.CartRepository;
import com.maito.cart.internal.scheduler.AbandonedCartCleanupTask;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class AbandonedCartCleanupTaskTest {

    @Autowired
    private AbandonedCartCleanupTask cleanupTask;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final TenantContext tenantContext = new TenantContext(
            "mito_crunch",
            "mitocrunch",
            "IN",
            "INR",
            "en_IN",
            "db_mitocrunch"
    );

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(tenantContext);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Assert AbandonedCartCleanupTask purges guest carts older than 30 days while preserving customer carts")
    void shouldPurgeAbandonedGuestCartsOlderThan30Days() {
        Instant thirtyFiveDaysAgo = Instant.now().minus(35, ChronoUnit.DAYS);

        // 1. Create an old guest cart (abandoned > 30 days)
        Cart oldGuestCart = Cart.builder()
                .guestCartId("guest-abandoned-" + UUID.randomUUID())
                .customerProfileId(null)
                .currencyCode("INR")
                .build();
        oldGuestCart = cartRepository.save(oldGuestCart);
        jdbcTemplate.update("UPDATE carts SET updated_at = ? WHERE id = ?", Timestamp.from(thirtyFiveDaysAgo), oldGuestCart.getId());

        // 2. Create a recent guest cart (active today)
        Cart recentGuestCart = Cart.builder()
                .guestCartId("guest-active-" + UUID.randomUUID())
                .customerProfileId(null)
                .currencyCode("INR")
                .build();
        recentGuestCart = cartRepository.save(recentGuestCart);

        // 3. Create an old customer cart (should NEVER be purged even if old)
        TenantProfileDto customer = userService.createProfile(
                UUID.randomUUID(), "CartUser", "CleanupTest", "ROLE_TENANT_CUSTOMER", List.of()
        );
        Cart customerCart = Cart.builder()
                .customerProfileId(customer.id())
                .currencyCode("INR")
                .build();
        customerCart = cartRepository.save(customerCart);
        jdbcTemplate.update("UPDATE carts SET updated_at = ? WHERE id = ?", Timestamp.from(thirtyFiveDaysAgo), customerCart.getId());

        // Execute purge for carts older than 30 days
        int purged = cleanupTask.purgeAbandonedGuestCarts(30);
        assertThat(purged).isGreaterThanOrEqualTo(1);

        // Verify: Old guest cart is purged
        assertThat(cartRepository.findById(oldGuestCart.getId())).isEmpty();

        // Verify: Recent guest cart is preserved
        assertThat(cartRepository.findById(recentGuestCart.getId())).isPresent();

        // Verify: Old customer cart is strictly preserved
        assertThat(cartRepository.findById(customerCart.getId())).isPresent();
    }
}
