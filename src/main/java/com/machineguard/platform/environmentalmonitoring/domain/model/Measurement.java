package com.machineguard.platform.environmentalmonitoring.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import java.util.Objects;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "measurements")
public class Measurement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(name = "monitoring_zone_id", nullable = false)
    private UUID monitoringZoneId;
    @Column(name = "monitoring_point_id", nullable = false)
    private UUID monitoringPointId;
    @Column(name = "sensor_node_id")
    private UUID sensorNodeId;
    @Enumerated(EnumType.STRING)
    @Column(name = "environmental_variable", nullable = false, length = 20)
    private EnvironmentalVariable environmentalVariable;
    @Column(name = "measured_value", nullable = false, precision = 6, scale = 2)
    private BigDecimal measuredValue;
    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;
    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    protected Measurement() {}

    public static Measurement record(UUID organizationId,
            UUID monitoringZoneId,
            UUID monitoringPointId,
            UUID sensorNodeId,
            EnvironmentalVariable environmentalVariable,
            BigDecimal measuredValue,
            OffsetDateTime recordedAt,
            OffsetDateTime receivedAt) {
        Measurement result = new Measurement();
        result.organizationId = Objects.requireNonNull(organizationId);
        result.monitoringZoneId = Objects.requireNonNull(monitoringZoneId);
        result.monitoringPointId = Objects.requireNonNull(monitoringPointId);
        result.sensorNodeId = sensorNodeId;
        result.environmentalVariable = Objects.requireNonNull(environmentalVariable);
        result.measuredValue = NumericValue.exact(measuredValue);
        result.recordedAt = Objects.requireNonNull(recordedAt);
        result.receivedAt = Objects.requireNonNull(receivedAt);
        return result;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getMonitoringZoneId() { return monitoringZoneId; }
    public UUID getMonitoringPointId() { return monitoringPointId; }
    public UUID getSensorNodeId() { return sensorNodeId; }
    public EnvironmentalVariable getEnvironmentalVariable() { return environmentalVariable; }
    public BigDecimal getMeasuredValue() { return measuredValue; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
}
