package com.machineguard.platform.environmentalmonitoring.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
public class MeasurementTimePolicy {
    public static class InvalidCaptureTimeException extends IllegalArgumentException {
        public InvalidCaptureTimeException(String message){super(message);}
    }
    private final Clock clock;
    private final Instant minimum;
    private final Duration futureTolerance;
    private final Duration maximumAge;

    public MeasurementTimePolicy(Clock clock,
            @Value("${machineguard.monitoring.max-future-seconds:60}") long futureSeconds,
            @Value("${machineguard.monitoring.max-reading-age-days:30}") long ageDays,
            @Value("${machineguard.monitoring.min-recorded-at:2026-01-01T00:00:00Z}") String minimum) {
        if (futureSeconds < 0 || ageDays <= 0) throw new IllegalArgumentException("Invalid measurement time limits");
        this.clock=clock;
        this.minimum=Instant.parse(minimum);
        this.futureTolerance=Duration.ofSeconds(futureSeconds);
        this.maximumAge=Duration.ofDays(ageDays);
        // Fail at startup if configuration would overflow the server's clock range.
        clock.instant().plus(futureTolerance);
        clock.instant().minus(maximumAge);
    }

    public void validate(OffsetDateTime recordedAt) {
        if (recordedAt == null) throw new InvalidCaptureTimeException("Capture time is required");
        Instant at=recordedAt.toInstant(), now=clock.instant();
        if (at.isBefore(minimum)) throw new InvalidCaptureTimeException("Capture precedes the configured minimum");
        if (at.isAfter(now.plus(futureTolerance))) throw new InvalidCaptureTimeException("Capture exceeds the future tolerance");
        if (at.isBefore(now.minus(maximumAge))) throw new InvalidCaptureTimeException("Capture exceeds the maximum age");
    }
}
