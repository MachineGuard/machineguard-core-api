package com.machineguard.platform.iam.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Single-instance, bounded fixed windows; no forwarded identity or raw email stored. */
@Component
public class LoginRateLimiter {
    private final Clock clock;
    private final int limit,maxKeys;
    private final Duration window;
    private final Map<String,Counter> counters=new HashMap<>();
    private static class Counter {
        final Instant until;int attempts;
        Counter(Instant until) { this.until=until; }
    }
    public LoginRateLimiter(Clock clock,@Value("${machineguard.iam.login-limit:5}") int limit,
            @Value("${machineguard.iam.login-window-seconds:60}") long seconds,
            @Value("${machineguard.iam.login-max-keys:10000}") int maxKeys) {
        if(limit<1 || limit>100 || seconds<1 || seconds>3600 || maxKeys<2 || maxKeys>100000)
            throw new IllegalArgumentException("Invalid login rate limit");
        this.clock=clock;this.limit=limit;this.window=Duration.ofSeconds(seconds);this.maxKeys=maxKeys;
    }
    /** Zero allows this attempt. Positive values are Retry-After seconds. */
    public synchronized long attempt(String email,String remoteAddress) {
        Instant now=clock.instant();counters.entrySet().removeIf(e->!e.getValue().until.isAfter(now));
        String[] keys={"email:"+hash(email.trim().toLowerCase(Locale.ROOT)),"ip:"+hash(remoteAddress)};
        long missing=Arrays.stream(keys).filter(k->!counters.containsKey(k)).count();
        if(counters.size()+missing>maxKeys)return window.toSeconds();
        long retry=0;
        for(String key:keys) {
            Counter counter=counters.computeIfAbsent(key,k->new Counter(now.plus(window)));
            counter.attempts=Math.min(limit+1,counter.attempts+1);
            if(counter.attempts>limit)retry=Math.max(retry,Math.max(1,(Duration.between(now,counter.until).toMillis()+999)/1000));
        }
        return retry;
    }
    public synchronized int trackedKeys() { return counters.size(); }
    private static String hash(String text) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
