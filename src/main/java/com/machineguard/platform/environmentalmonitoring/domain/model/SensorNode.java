package com.machineguard.platform.environmentalmonitoring.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import java.util.Objects;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.EnvironmentalMonitoringRuleException;

@Entity
@Table(name = "sensor_nodes")
public class SensorNode {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "monitoring_point_id", nullable = false)
    private UUID monitoringPointId;
    @Column(name = "device_code", nullable = false, length = 100)
    private String deviceCode;
    @Column(name = "sampling_interval_seconds", nullable = false)
    private int samplingIntervalSeconds;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SensorNodeStatus status;
    @Column(name = "last_measurement_at")
    private OffsetDateTime lastMeasurementAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SensorNode() {}

    public static SensorNode register(UUID monitoringPointId,
            String deviceCode,
            int samplingIntervalSeconds,
            OffsetDateTime now) {
        SensorNode result = new SensorNode();
        if (samplingIntervalSeconds <= 0) throw new EnvironmentalMonitoringRuleException("Sampling interval must be positive");
        result.monitoringPointId = Objects.requireNonNull(monitoringPointId);
        result.deviceCode = Objects.requireNonNull(deviceCode);
        result.samplingIntervalSeconds = samplingIntervalSeconds;
        result.status = SensorNodeStatus.OFFLINE;
        result.createdAt = now;
        return result;
    }

    public UUID getId() { return id; }
    public UUID getMonitoringPointId() { return monitoringPointId; }
    public String getDeviceCode() { return deviceCode; }
    public int getSamplingIntervalSeconds() { return samplingIntervalSeconds; }
    public SensorNodeStatus getStatus() { return status; }
    public OffsetDateTime getLastMeasurementAt() { return lastMeasurementAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void recordMeasurement(OffsetDateTime time) {
        if (lastMeasurementAt == null || time.isAfter(lastMeasurementAt)) {
            lastMeasurementAt = time;
            status = SensorNodeStatus.ONLINE;
        }
    }
}
