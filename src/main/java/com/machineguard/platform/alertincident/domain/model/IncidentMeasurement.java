package com.machineguard.platform.alertincident.domain.model;

import jakarta.persistence.*;
import java.util.UUID;

/** Immutable association for each deviation absorbed by an incident. */
@Entity
@Table(name = "incident_measurements")
public class IncidentMeasurement {
    @Id @Column(name = "measurement_id")
    private UUID measurementId;
    @Column(name = "incident_id", nullable = false, updatable = false)
    private UUID incidentId;
    protected IncidentMeasurement() { }
    public IncidentMeasurement(UUID measurementId, UUID incidentId) {
        this.measurementId = measurementId;
        this.incidentId = incidentId;
    }
    public UUID getIncidentId() { return incidentId; }
}
