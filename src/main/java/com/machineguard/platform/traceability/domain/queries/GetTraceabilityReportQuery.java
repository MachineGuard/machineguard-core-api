package com.machineguard.platform.traceability.domain.queries;

import java.util.UUID;

public record GetTraceabilityReportQuery(UUID organizationId, UUID reportId) {
}
