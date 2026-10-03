package com.machineguard.platform.traceability.domain.model;

import java.time.Duration;
import java.time.Instant;

public record ExcursionPeriod(Instant startedAt, Instant endedAt) {

    public ExcursionPeriod {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt es obligatorio");
        }
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt no puede ser anterior a startedAt");
        }
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public Duration duration(Instant now) {
        return Duration.between(startedAt, isOpen() ? now : endedAt);
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(startedAt) && (isOpen() || !instant.isAfter(endedAt));
    }

    public boolean overlaps(ExcursionPeriod other) {
        boolean startsBeforeOtherEnds = other.isOpen() || !startedAt.isAfter(other.endedAt);
        boolean otherStartsBeforeEnds = isOpen() || !other.startedAt.isAfter(endedAt);
        return startsBeforeOtherEnds && otherStartsBeforeEnds;
    }
}
