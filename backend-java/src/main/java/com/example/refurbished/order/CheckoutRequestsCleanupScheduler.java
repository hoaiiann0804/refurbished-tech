package com.example.refurbished.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.order.checkout-cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class CheckoutRequestsCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(CheckoutRequestsCleanupScheduler.class);
    private final CheckoutRequests checkoutRequests;
    private final int retentionDays;

    public CheckoutRequestsCleanupScheduler(
            CheckoutRequests checkoutRequests,
            @Value("${app.order.checkout-retention-days:7}") int retentionDays) {
        this.checkoutRequests = checkoutRequests;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.order.checkout-cleanup-cron:0 0 3 * * *}")
    public void cleanup() {
        int deleted = checkoutRequests.cleanupOldRequests(retentionDays);
        if (deleted > 0) {
            log.info("Cleaned up {} expired checkout idempotency records older than {} days", deleted, retentionDays);
        }
    }
}
