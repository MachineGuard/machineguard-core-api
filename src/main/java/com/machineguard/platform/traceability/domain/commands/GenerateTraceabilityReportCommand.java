package com.machineguard.platform.traceability.domain.commands;

import java.time.Instant;
import java.util.UUID;

public record GenerateTraceabilityReportCommand(UUID organizationId, UUID monitoringZoneId, Instant periodStart,
                                                Instant periodEnd, UUID generatedBy) {
}
