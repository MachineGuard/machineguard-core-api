package com.machineguard.platform.traceability.domain.events;

import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;

import java.time.Instant;

/** Eventos de dominio publicados por Traceability & Quality. */
public final class TraceabilityEvents {

    private TraceabilityEvents() {
    }

    public record ExcursionStarted(Long excursionId, Long organizationId, Long monitoringZoneId,
                                   EnvironmentalVariable environmentalVariable, Instant startedAt) {
    }

    public record ExcursionEnded(Long excursionId, Long organizationId, Long monitoringZoneId, Instant endedAt,
                                 long durationSeconds, double peakValue) {
    }

    public record TraceabilityReportGenerated(Long reportId, Long organizationId, Long monitoringZoneId,
                                              Instant generatedAt, String checksum) {
    }
}
