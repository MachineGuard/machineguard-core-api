package com.machineguard.platform.environmentalmonitoring.domain.events;

import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class EnvironmentalMonitoringEvents {
    private EnvironmentalMonitoringEvents() {}
    public record DeviationDetected(UUID measurementId, UUID organizationId, UUID monitoringZoneId,
            UUID monitoringPointId, UUID sensorNodeId, EnvironmentalVariable environmentalVariable,
            BigDecimal measuredValue, BigDecimal minimumValue, BigDecimal maximumValue, OffsetDateTime recordedAt) {}
    public record SafeRangeRestored(UUID measurementId, UUID organizationId, UUID monitoringZoneId,
            UUID monitoringPointId, UUID sensorNodeId, EnvironmentalVariable environmentalVariable,
            BigDecimal measuredValue, OffsetDateTime recordedAt, OffsetDateTime restoredAt) {}

    public record ThresholdConfigured(UUID thresholdId, UUID organizationId, UUID monitoringZoneId,
            EnvironmentalVariable environmentalVariable, BigDecimal minimumValue, BigDecimal maximumValue,
            OffsetDateTime configuredAt) {}
}
