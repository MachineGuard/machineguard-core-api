package com.machineguard.platform.environmentalmonitoring.application;

import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
public class MeasurementFreshnessPolicy {
    private final Clock clock;
    private final long minimumSeconds, intervalMultiplier, futureSeconds;
    public MeasurementFreshnessPolicy(Clock clock,
            @Value("${machineguard.monitoring.freshness-minimum-seconds:30}") long minimumSeconds,
            @Value("${machineguard.monitoring.freshness-interval-multiplier:3}") long intervalMultiplier,
            @Value("${machineguard.monitoring.max-future-seconds:60}") long futureSeconds) {
        if (minimumSeconds<1 || intervalMultiplier<1 || minimumSeconds>86400 || intervalMultiplier>100 || futureSeconds<0)
            throw new IllegalArgumentException("Invalid monitoring freshness policy");
        this.clock=clock;this.minimumSeconds=minimumSeconds;this.intervalMultiplier=intervalMultiplier;this.futureSeconds=futureSeconds;
    }
    public long window(SensorNode node) { return Math.max(minimumSeconds,intervalMultiplier*node.getSamplingIntervalSeconds()); }
    public long age(OffsetDateTime at) { return Duration.between(at.toInstant(),clock.instant()).getSeconds(); }
    public boolean receivedFresh(Measurement m,SensorNode node) {
        Instant received=m.getReceivedAt().toInstant(),now=clock.instant();
        return m.getSource()==MeasurementSource.SENSOR && !received.isBefore(now.minusSeconds(window(node))) && !received.isAfter(now);
    }
    public boolean fresh(Measurement m,SensorNode node) {
        Instant capture=m.getRecordedAt().toInstant(),now=clock.instant();
        return receivedFresh(m,node) && !capture.isBefore(now.minusSeconds(window(node))) && !capture.isAfter(now.plusSeconds(futureSeconds));
    }
}
