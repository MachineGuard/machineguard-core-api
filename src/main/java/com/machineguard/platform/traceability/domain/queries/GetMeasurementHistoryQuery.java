package com.machineguard.platform.traceability.domain.queries;

import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;

import java.time.Instant;
import java.util.UUID;

public record GetMeasurementHistoryQuery(UUID organizationId, UUID monitoringZoneId, EnvironmentalVariable variable,
                                         Instant from, Instant to) {
}
