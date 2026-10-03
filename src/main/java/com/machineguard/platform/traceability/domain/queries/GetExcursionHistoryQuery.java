package com.machineguard.platform.traceability.domain.queries;

import com.machineguard.platform.traceability.domain.model.ExcursionStatus;

import java.time.Instant;

/** Todos los filtros salvo organizationId son opcionales. */
public record GetExcursionHistoryQuery(Long organizationId, Long monitoringZoneId, Instant from, Instant to,
                                       ExcursionStatus status) {
}
