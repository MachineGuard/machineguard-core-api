package com.machineguard.platform.traceability.domain.events;

import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;

import java.time.Instant;
import java.util.UUID;

/** Eventos de dominio publicados por Traceability & Quality. */
public final class TraceabilityEvents {

    private TraceabilityEvents() {
    }

    public record ExcursionStarted(UUID excursionId, UUID organizationId, UUID monitoringZoneId,
                                   EnvironmentalVariable environmentalVariable, Instant startedAt) {
    }

    public record ExcursionEnded(UUID excursionId, UUID organizationId, UUID monitoringZoneId, Instant endedAt,
                                 long durationSeconds, double peakValue) {
    }

    public record TraceabilityReportGenerated(UUID reportId, UUID organizationId, UUID monitoringZoneId,
                                              Instant generatedAt, String checksum) {
    }
}
