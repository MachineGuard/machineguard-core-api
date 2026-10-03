package com.machineguard.platform.traceability.domain.model;

import java.time.Duration;

/**
 * Gravedad de una excursion segun la magnitud de la desviacion (distancia del valor pico al umbral)
 * y su duracion. Una excursion que supera {@link #ESCALATION_DURATION} sube un nivel.
 */
public enum ExcursionSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static final Duration ESCALATION_DURATION = Duration.ofMinutes(60);

    public static ExcursionSeverity classify(double magnitude, Duration duration) {
        ExcursionSeverity base;
        if (magnitude < 2) {
            base = LOW;
        } else if (magnitude < 5) {
            base = MEDIUM;
        } else if (magnitude < 10) {
            base = HIGH;
        } else {
            base = CRITICAL;
        }
        return duration.compareTo(ESCALATION_DURATION) >= 0 ? base.escalate() : base;
    }

    public ExcursionSeverity escalate() {
        return this == CRITICAL ? CRITICAL : values()[ordinal() + 1];
    }

    public ExcursionSeverity max(ExcursionSeverity other) {
        return this.ordinal() >= other.ordinal() ? this : other;
    }
}
