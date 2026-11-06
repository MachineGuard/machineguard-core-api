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
    public record RegisterMonitoringZoneResource(@NotBlank @Size(max = 150) String name, @Size(max = 500) String description,
            UUID facilityId) {}
    public record RegisterMonitoringPointResource(@NotBlank @Size(max = 150) String name, @Size(max = 250) String location) {}
    public record RegisterSensorNodeResource(@NotBlank @Size(max = 100) String deviceCode,
            @NotNull @Positive Integer samplingIntervalSeconds) {}
    public record ConfigureThresholdResource(@NotNull @Digits(integer = 4, fraction = 2) BigDecimal minimumValue,
            @NotNull @Digits(integer = 4, fraction = 2) BigDecimal maximumValue) {}
    public record MeasurementResource(UUID id, UUID organizationId, UUID monitoringZoneId, UUID monitoringPointId,
            UUID sensorNodeId, EnvironmentalVariable environmentalVariable, BigDecimal measuredValue,
            OffsetDateTime recordedAt, OffsetDateTime receivedAt,MeasurementSource source,String freshness,
            Long freshnessWindowSeconds,long capturedAgeSeconds,long receivedAgeSeconds) {}
    public record MeasurementHistoryResource(List<MeasurementResource> items, int page, int size, long totalElements, int totalPages) {}
    public record ThresholdResource(UUID id, EnvironmentalVariable environmentalVariable, BigDecimal minimumValue, BigDecimal maximumValue) {}
    public record PointResource(UUID id, String name, String location, MonitoringPointStatus status) {}
    public record SensorResource(UUID id, UUID monitoringPointId, String deviceCode, int samplingIntervalSeconds,
            SensorNodeStatus status, OffsetDateTime lastMeasurementAt,String administrativeStatus,String availability,
            OffsetDateTime lastReceivedAt,Long receivedAgeSeconds,long freshnessWindowSeconds) {}
    public record ZoneResource(UUID id, UUID organizationId, UUID facilityId, String name, String description,
            MonitoringZoneStatus status, String environmentalCondition, int sensorsOnline, int sensorsOffline,
            int sensorsInactive, OffsetDateTime lastUpdatedAt, List<PointResource> points, List<SensorResource> sensors,
            List<ThresholdResource> thresholds, List<MeasurementResource> latestMeasurements,String freshness,List<MeasurementResource> latestPhysicalMeasurements) {}
}
