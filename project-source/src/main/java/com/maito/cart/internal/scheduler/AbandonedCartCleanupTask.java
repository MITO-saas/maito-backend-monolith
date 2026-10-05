package com.maito.cart.internal.scheduler;

import com.maito.cart.internal.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled maintenance job that purges inactive guest carts older than 30 days.
 * Preserves customer carts indefinitely.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbandonedCartCleanupTask {

    private final CartRepository cartRepository;

    @Scheduled(cron = "${maito.cart.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public int executeScheduledPurge() {
        return purgeAbandonedGuestCarts(30);
    }

    @Transactional
    public int purgeAbandonedGuestCarts(int daysThreshold) {
        Instant cutoff = Instant.now().minus(daysThreshold, ChronoUnit.DAYS);
        int deletedCount = cartRepository.deleteInactiveGuestCartsOlderThan(cutoff);
        log.info("AbandonedCartCleanupTask: purged [{}] inactive guest carts older than [{}] days (cutoff: {})",
                deletedCount, daysThreshold, cutoff);
        return deletedCount;
    }
}
