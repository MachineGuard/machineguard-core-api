package com.machineguard.platform.traceability.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="late_deviations")
public class LateDeviation {
    @Id @Column(name="measurement_id") private UUID measurementId;
    @Column(name="organization_id",nullable=false) private UUID organizationId;
    @Column(name="monitoring_zone_id",nullable=false) private UUID monitoringZoneId;
    @Column(name="monitoring_point_id",nullable=false) private UUID monitoringPointId;
    @Column(name="sensor_node_id") private UUID sensorNodeId;
    @Enumerated(EnumType.STRING) @Column(name="environmental_variable",nullable=false) private EnvironmentalVariable variable;
    @Column(name="measured_value",nullable=false,precision=6,scale=2) private BigDecimal measuredValue;
    @Column(name="minimum_value",nullable=false,precision=6,scale=2) private BigDecimal minimumValue;
    @Column(name="maximum_value",nullable=false,precision=6,scale=2) private BigDecimal maximumValue;
    @Column(name="recorded_at",nullable=false) private Instant recordedAt;
    @Column(name="received_at",nullable=false) private Instant receivedAt;

    protected LateDeviation() { }
    public static LateDeviation record(UUID measurementId,UUID organizationId,UUID zoneId,UUID pointId,UUID sensorId,
            EnvironmentalVariable variable,BigDecimal measuredValue,BigDecimal minimumValue,BigDecimal maximumValue,
            Instant recordedAt,Instant receivedAt) {
        var value=new LateDeviation();
        value.measurementId=measurementId;value.organizationId=organizationId;value.monitoringZoneId=zoneId;
        value.monitoringPointId=pointId;value.sensorNodeId=sensorId;value.variable=variable;value.measuredValue=measuredValue;
        value.minimumValue=minimumValue;value.maximumValue=maximumValue;value.recordedAt=recordedAt;value.receivedAt=receivedAt;
        return value;
    }
    public UUID getMeasurementId(){return measurementId;}
    public UUID getOrganizationId(){return organizationId;}
    public UUID getMonitoringZoneId(){return monitoringZoneId;}
    public UUID getMonitoringPointId(){return monitoringPointId;}
    public UUID getSensorNodeId(){return sensorNodeId;}
    public EnvironmentalVariable getVariable(){return variable;}
    public BigDecimal getMeasuredValue(){return measuredValue;}
    public BigDecimal getMinimumValue(){return minimumValue;}
    public BigDecimal getMaximumValue(){return maximumValue;}
    public Instant getRecordedAt(){return recordedAt;}
    public Instant getReceivedAt(){return receivedAt;}
}
