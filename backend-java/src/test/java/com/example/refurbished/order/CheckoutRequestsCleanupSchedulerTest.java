package com.example.refurbished.order;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckoutRequestsCleanupSchedulerTest {

    @Test
    void cleanup_invokesRepositoryCleanup() {
        AtomicInteger cleanedDays = new AtomicInteger(-1);
        CheckoutRequests stubRequests = new CheckoutRequests(null) {
            @Override
            public int cleanupOldRequests(int retentionDays) {
                cleanedDays.set(retentionDays);
                return 3;
            }
        };

        CheckoutRequestsCleanupScheduler scheduler = new CheckoutRequestsCleanupScheduler(stubRequests, 7);
        scheduler.cleanup();

        assertEquals(7, cleanedDays.get());
    }
}
