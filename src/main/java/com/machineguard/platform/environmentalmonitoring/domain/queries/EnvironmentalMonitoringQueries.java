package com.machineguard.platform.environmentalmonitoring.domain.queries;

import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class EnvironmentalMonitoringQueries {
    private EnvironmentalMonitoringQueries() {}
    public record GetMeasurement(UUID organizationId, UUID measurementId) {}
    public record GetLatestMeasurements(UUID organizationId, UUID monitoringZoneId) {}
    public record GetMeasurementHistory(UUID organizationId, UUID monitoringZoneId, UUID monitoringPointId,
            EnvironmentalVariable environmentalVariable, OffsetDateTime from, OffsetDateTime to, int page, int size) {}
    public record GetZones(UUID organizationId) {}
    public record GetZone(UUID organizationId, UUID monitoringZoneId) {}
}
