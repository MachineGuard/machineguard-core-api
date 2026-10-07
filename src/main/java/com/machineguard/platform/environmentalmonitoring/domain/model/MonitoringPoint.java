package com.machineguard.platform.environmentalmonitoring.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import java.util.Objects;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "monitoring_points")
public class MonitoringPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "monitoring_zone_id", nullable = false)
    private UUID monitoringZoneId;
    @Column(name = "name", nullable = false, length = 150)
    private String name;
    @Column(name = "location", length = 250)
    private String location;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MonitoringPointStatus status;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MonitoringPoint() {}

    public static MonitoringPoint register(UUID monitoringZoneId, String name, String location, OffsetDateTime now) {
        MonitoringPoint result = new MonitoringPoint();
        result.monitoringZoneId = Objects.requireNonNull(monitoringZoneId);
        result.name = Objects.requireNonNull(name);
        result.location = location;
        result.status = MonitoringPointStatus.ACTIVE;
        result.createdAt = now;
        result.updatedAt = now;
        return result;
    }

    public UUID getId() { return id; }
    public UUID getMonitoringZoneId() { return monitoringZoneId; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public MonitoringPointStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
