package com.machineguard.platform.environmentalmonitoring.interfaces.rest.resources;

import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public final class EnvironmentalMonitoringResources {
    private EnvironmentalMonitoringResources() {}
    public record RegisterMeasurementResource(@NotNull UUID monitoringZoneId, @NotNull UUID monitoringPointId,
            UUID sensorNodeId, @NotNull EnvironmentalVariable environmentalVariable,
            @NotNull @Digits(integer = 4, fraction = 2) BigDecimal measuredValue, @NotNull OffsetDateTime recordedAt) {}
    public record MeasurementResource(UUID id, UUID organizationId, UUID monitoringZoneId, UUID monitoringPointId,
            UUID sensorNodeId, EnvironmentalVariable environmentalVariable, BigDecimal measuredValue,
            OffsetDateTime recordedAt, OffsetDateTime receivedAt) {}
    public record MeasurementHistoryResource(List<MeasurementResource> items, int page, int size, long totalElements, int totalPages) {}
    public record ThresholdResource(UUID id, EnvironmentalVariable environmentalVariable, BigDecimal minimumValue, BigDecimal maximumValue) {}
    public record PointResource(UUID id, String name, String location, MonitoringPointStatus status) {}
    public record SensorResource(UUID id, UUID monitoringPointId, String deviceCode, int samplingIntervalSeconds,
            SensorNodeStatus status, OffsetDateTime lastMeasurementAt) {}
    public record ZoneResource(UUID id, UUID organizationId, UUID facilityId, String name, String description,
            MonitoringZoneStatus status, String environmentalCondition, int sensorsOnline, int sensorsOffline,
            int sensorsInactive, OffsetDateTime lastUpdatedAt, List<PointResource> points, List<SensorResource> sensors,
            List<ThresholdResource> thresholds, List<MeasurementResource> latestMeasurements) {}
}
