package com.machineguard.platform.traceability.domain.model;

import java.time.Instant;
import java.time.ZoneOffset;

public record RetentionPeriod(int months) {

    public RetentionPeriod {
        if (months <= 0) {
            throw new IllegalArgumentException("El Retention Period debe ser mayor que cero");
        }
    }

    public Instant expirationDateFrom(Instant recordedAt) {
        return recordedAt.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    public boolean isExpired(Instant recordedAt, Instant now) {
        return !expirationDateFrom(recordedAt).isAfter(now);
    }
}
