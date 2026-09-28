package com.example.refurbished.security;

import com.example.refurbished.common.exception.RateLimitExceededException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final long windowMillis;
    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${app.security.rate-limit.login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.rate-limit.login.window-seconds:60}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000L;
    }

    public void checkLimit(String key) {
        long now = System.currentTimeMillis();
        AttemptWindow window = attempts.compute(key, (k, current) -> {
            if (current == null || now - current.startTime >= windowMillis) {
                return new AttemptWindow(now, 1);
            }
            return new AttemptWindow(current.startTime, current.count + 1);
        });

        if (window.count > maxAttempts) {
            throw new RateLimitExceededException("Too many login attempts. Please try again after 1 minute.");
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> now - entry.getValue().startTime >= windowMillis);
    }

    public int getAttemptCount(String key) {
        AttemptWindow window = attempts.get(key);
        return window != null ? window.count : 0;
    }

    record AttemptWindow(long startTime, int count) {}
}
