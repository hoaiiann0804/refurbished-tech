package com.example.refurbished.online;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="app.online.expiry-enabled",havingValue="true",matchIfMissing=true)
public class ReservationExpiry {
    private final OnlineService online;
    public ReservationExpiry(OnlineService online) { this.online=online; }
    @Scheduled(fixedDelayString="${app.online.expiry-interval-ms:30000}",initialDelay=30000)
    public void expire() { online.expireBatch(); }
}
