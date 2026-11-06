package com.machineguard.platform.iam;

import com.machineguard.platform.iam.application.LoginRateLimiter;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {
    static class MutableClock extends Clock {
        Instant now=Instant.parse("2026-11-04T12:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    @Test void windowsExpireAndStateRemainsBoundedWithoutEvictingActiveLimits() {
        var clock=new MutableClock();var limiter=new LoginRateLimiter(clock,5,60,4);
        for(int i=0;i<5;i++)assertEquals(0,limiter.attempt(" User@f5.invalid ","127.0.0.1"));
        assertEquals(60,limiter.attempt("user@f5.invalid","127.0.0.2"));
        assertTrue(limiter.attempt("another@f5.invalid","127.0.0.3")>0);
        for(int i=0;i<100;i++)limiter.attempt(i+"@f5.invalid","127.2.0."+i);
        assertTrue(limiter.trackedKeys()<=4);
        clock.now=clock.now.plusSeconds(59);assertEquals(1,limiter.attempt("user@f5.invalid","127.0.0.1"));
        clock.now=clock.now.plusSeconds(1);assertEquals(0,limiter.attempt("user@f5.invalid","127.0.0.1"));
        assertEquals(2,limiter.trackedKeys());
    }
}
