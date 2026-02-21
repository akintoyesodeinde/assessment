package com.example.test.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRateLimiter {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitDecision tryAcquire(String key, int maxRequests, Duration window) {
        if (maxRequests <= 0 || window.isNegative() || window.isZero()) {
            return new RateLimitDecision(false, 1L);
        }
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now));
        synchronized (counter) {
            long elapsed = now - counter.windowStartMs;
            long windowMs = window.toMillis();
            if (elapsed >= windowMs) {
                counter.windowStartMs = now;
                counter.requests = 0;
            }
            if (counter.requests >= maxRequests) {
                long retryAfterMs = windowMs - (now - counter.windowStartMs);
                long retryAfterSeconds = Math.max(1L, (long) Math.ceil(retryAfterMs / 1000.0));
                return new RateLimitDecision(false, retryAfterSeconds);
            }
            counter.requests++;
        }
        cleanupIfNecessary(now);
        return new RateLimitDecision(true, 0L);
    }

    private void cleanupIfNecessary(long now) {
        if (counters.size() < 10_000) {
            return;
        }
        Iterator<Map.Entry<String, WindowCounter>> iterator = counters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, WindowCounter> entry = iterator.next();
            WindowCounter counter = entry.getValue();
            synchronized (counter) {
                if (now - counter.windowStartMs > Duration.ofHours(1).toMillis()) {
                    iterator.remove();
                }
            }
        }
    }

    private static final class WindowCounter {
        private long windowStartMs;
        private int requests;

        private WindowCounter(long now) {
            this.windowStartMs = now;
            this.requests = 0;
        }
    }

    public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    }
}
