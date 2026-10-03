package com.machineguard.platform.traceability.domain.queries;

import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;

import java.time.Instant;

public record GetMeasurementHistoryQuery(Long organizationId, Long monitoringZoneId, EnvironmentalVariable variable,
                                         Instant from, Instant to) {
}
