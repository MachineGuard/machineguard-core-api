package com.machineguard.platform.traceability.domain.queries;

import com.machineguard.platform.traceability.domain.model.ExcursionStatus;

import java.time.Instant;
import java.util.UUID;

/** Todos los filtros salvo organizationId son opcionales. */
public record GetExcursionHistoryQuery(UUID organizationId, UUID monitoringZoneId, Instant from, Instant to,
                                       ExcursionStatus status) {
}
