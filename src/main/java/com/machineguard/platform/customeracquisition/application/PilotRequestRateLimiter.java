package com.machineguard.platform.customeracquisition.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Single-instance fixed window per client address, stored as a SHA-256 hash. Forwarded headers are not trusted,
 * so behind a reverse proxy all clients share the proxy address. The map is bounded and fails closed when full.
 */
@Component
public class PilotRequestRateLimiter {
    private final Clock clock;
    private final int limit;
    private final Duration window;
    private final int maxKeys;
    private final Map<String, Window> windows = new HashMap<>();

    private static final class Window {
        final Instant until;
        int attempts;
        Window(Instant until) { this.until = until; }
    }

    public PilotRequestRateLimiter(Clock clock,
                                   @Value("${machineguard.acquisition.pilot-ip-limit:20}") int limit,
                                   @Value("${machineguard.acquisition.pilot-window-seconds:3600}") long seconds,
                                   @Value("${machineguard.acquisition.pilot-max-keys:10000}") int maxKeys) {
        if (limit < 1 || limit > 1000 || seconds < 60 || seconds > 86_400 || maxKeys < 2 || maxKeys > 100_000) {
            throw new IllegalArgumentException("Invalid pilot request rate limit");
        }
        this.clock = clock;
        this.limit = limit;
        this.window = Duration.ofSeconds(seconds);
        this.maxKeys = maxKeys;
    }

    /** Zero allows the attempt; a positive value is the Retry-After in seconds. */
    public synchronized long attempt(String remoteAddress) {
        Instant now = clock.instant();
        windows.values().removeIf(w -> !w.until.isAfter(now));
        String key = hash(remoteAddress == null ? "unknown" : remoteAddress);
        Window current = windows.get(key);
        if (current == null) {
            if (windows.size() >= maxKeys) return window.toSeconds();
            current = new Window(now.plus(window));
            windows.put(key, current);
        }
        if (current.attempts >= limit) {
            return Math.max(1, (Duration.between(now, current.until).toMillis() + 999) / 1000);
        }
        current.attempts++;
        return 0;
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
