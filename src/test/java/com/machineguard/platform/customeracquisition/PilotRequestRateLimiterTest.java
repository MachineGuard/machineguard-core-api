package com.machineguard.platform.customeracquisition;

import com.machineguard.platform.customeracquisition.application.PilotRequestRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class PilotRequestRateLimiterTest {
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-07T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test void allowsTheConfiguredNumberOfAttemptsPerAddressThenReturnsRetryAfter() {
        var clock = new MutableClock();
        var limiter = new PilotRequestRateLimiter(clock, 2, 60, 10);
        assertEquals(0, limiter.attempt("203.0.113.9"));
        assertEquals(0, limiter.attempt("203.0.113.9"));
        long retry = limiter.attempt("203.0.113.9");
        assertTrue(retry >= 1 && retry <= 60, "Retry-After must be within the window: " + retry);
        assertEquals(0, limiter.attempt("203.0.113.10"), "another address keeps its own window");
    }

    @Test void windowResetsAfterItExpires() {
        var clock = new MutableClock();
        var limiter = new PilotRequestRateLimiter(clock, 1, 60, 10);
        assertEquals(0, limiter.attempt("203.0.113.9"));
        assertTrue(limiter.attempt("203.0.113.9") > 0);
        clock.now = clock.now.plusSeconds(61);
        assertEquals(0, limiter.attempt("203.0.113.9"));
    }

    @Test void failsClosedWhenTheBoundedKeyMapIsFull() {
        var clock = new MutableClock();
        var limiter = new PilotRequestRateLimiter(clock, 5, 60, 2);
        assertEquals(0, limiter.attempt("203.0.113.1"));
        assertEquals(0, limiter.attempt("203.0.113.2"));
        assertEquals(60, limiter.attempt("203.0.113.3"));
    }

    @Test void rejectsUnsafeConfiguration() {
        var clock = new MutableClock();
        assertThrows(IllegalArgumentException.class, () -> new PilotRequestRateLimiter(clock, 0, 60, 10));
        assertThrows(IllegalArgumentException.class, () -> new PilotRequestRateLimiter(clock, 5, 30, 10));
        assertThrows(IllegalArgumentException.class, () -> new PilotRequestRateLimiter(clock, 5, 60, 1));
    }
}
