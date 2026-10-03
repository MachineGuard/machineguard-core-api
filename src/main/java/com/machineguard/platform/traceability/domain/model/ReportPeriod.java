package com.machineguard.platform.traceability.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record ReportPeriod(Instant from, Instant to) {

    public ReportPeriod {
        if (from == null || to == null) {
            throw new IllegalArgumentException("El periodo requiere fecha de inicio y de fin");
        }
        from = from.truncatedTo(ChronoUnit.MILLIS);
        to = to.truncatedTo(ChronoUnit.MILLIS);
    }

    public boolean isValid(Instant now, long maxDays) {
        return from.isBefore(to) && !to.isAfter(now) && lengthInDays() <= maxDays;
    }

    public boolean covers(Instant instant) {
        return !instant.isBefore(from) && !instant.isAfter(to);
    }

    public long lengthInDays() {
        return Duration.between(from, to).toDays();
    }
}
