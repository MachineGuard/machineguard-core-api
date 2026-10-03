package com.machineguard.platform.traceability.domain.commands;

import java.time.Instant;

public record GenerateTraceabilityReportCommand(Long organizationId, Long monitoringZoneId, Instant periodStart,
                                                Instant periodEnd, Long generatedBy) {
}
