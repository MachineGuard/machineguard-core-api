package com.machineguard.platform.environmentalmonitoring.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import java.util.Objects;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "monitoring_zones")
public class MonitoringZone {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(name = "facility_id")
    private UUID facilityId;
    @Column(name = "name", nullable = false, length = 150)
    private String name;
    @Column(name = "description", length = 500)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MonitoringZoneStatus status;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MonitoringZone() {}

    public static MonitoringZone register(UUID organizationId,
            UUID facilityId,
            String name,
            String description,
            OffsetDateTime now) {
        MonitoringZone result = new MonitoringZone();
        result.organizationId = Objects.requireNonNull(organizationId);
        result.facilityId = facilityId;
        result.name = Objects.requireNonNull(name);
        result.description = description;
        result.status = MonitoringZoneStatus.ACTIVE;
        result.createdAt = now;
        result.updatedAt = now;
        return result;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getFacilityId() { return facilityId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public MonitoringZoneStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
