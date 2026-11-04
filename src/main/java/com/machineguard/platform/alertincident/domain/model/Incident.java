package com.machineguard.platform.alertincident.domain.model;

import com.machineguard.platform.alertincident.domain.exceptions.AlertIncidentRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root: incidente operativo originado por una desviacion ambiental. Concentra el estado del caso
 * (OPEN, ACKNOWLEDGED, ESCALATED, RESOLVED) y coordina la alerta y las acciones correctivas asociadas.
 */
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "origin_measurement_id", nullable = false, updatable = false)
    private UUID originMeasurementId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "monitoring_zone_id", nullable = false, updatable = false)
    private UUID monitoringZoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "environmental_variable", length = 20, updatable = false)
    private EnvironmentalVariable environmentalVariable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "open_key", length = 100)
    private String openKey;

    @Column(nullable = false)
    private boolean historical;

    @Column(name = "live_started_at")
    private Instant liveStartedAt;

    protected Incident() {
    }

    public static Incident open(UUID originMeasurementId, UUID organizationId, UUID monitoringZoneId,
                                EnvironmentalVariable environmentalVariable, Instant openedAt) {
        return open(originMeasurementId,organizationId,monitoringZoneId,environmentalVariable,openedAt,false);
    }

    public static Incident open(UUID originMeasurementId, UUID organizationId, UUID monitoringZoneId,
                                EnvironmentalVariable environmentalVariable, Instant openedAt, boolean historical) {
        Incident incident = new Incident();
        incident.originMeasurementId = Objects.requireNonNull(originMeasurementId);
        incident.organizationId = Objects.requireNonNull(organizationId);
        incident.monitoringZoneId = Objects.requireNonNull(monitoringZoneId);
        incident.environmentalVariable = Objects.requireNonNull(environmentalVariable);
        incident.status = IncidentStatus.OPEN;
        incident.openedAt = openedAt.truncatedTo(ChronoUnit.MILLIS);
        incident.openKey = openKeyOf(organizationId, monitoringZoneId, environmentalVariable);
        incident.historical = historical;
        incident.liveStartedAt = historical ? null : incident.openedAt;
        return incident;
    }

    public void recordLiveEvidence(Instant at) {
        requireUnresolved("agregar evidencia vigente");
        historical=false;
        Instant captured=at.truncatedTo(ChronoUnit.MILLIS);
        if (liveStartedAt==null || captured.isBefore(liveStartedAt)) liveStartedAt=captured;
    }

    public boolean isHistorical() { return historical; }
    public Instant getLiveStartedAt() { return liveStartedAt; }

    public static String openKeyOf(UUID organizationId, UUID monitoringZoneId, EnvironmentalVariable variable) {
        return organizationId + ":" + monitoringZoneId + ":" + variable;
    }

    /** El reconocimiento de la alerta actualiza el ciclo de atencion del incidente. */
    public void acknowledge() {
        requireUnresolved("reconocerlo");
        status = IncidentStatus.ACKNOWLEDGED;
    }

    public void escalate() {
        requireUnresolved("escalarlo");
        if (status == IncidentStatus.OPEN) {
            status = IncidentStatus.ESCALATED;
        }
    }

    /** El cierre formal exige validar antes que exista una accion correctiva (IncidentResolutionService). */
    public void resolve(UUID resolvedBy, String notes, Instant resolvedAt) {
        requireUnresolved("resolverlo");
        this.status = IncidentStatus.RESOLVED;
        this.resolvedBy = Objects.requireNonNull(resolvedBy);
        this.resolutionNotes = notes == null || notes.isBlank() ? null : notes.trim();
        this.resolvedAt = resolvedAt.truncatedTo(ChronoUnit.MILLIS);
        this.openKey = null;
    }

    public boolean isResolved() {
        return status == IncidentStatus.RESOLVED;
    }

    public void requireUnresolved(String action) {
        if (isResolved()) {
            throw new AlertIncidentRuleException("El incidente " + id + " ya esta resuelto: no se puede " + action);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOriginMeasurementId() {
        return originMeasurementId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getMonitoringZoneId() {
        return monitoringZoneId;
    }

    public EnvironmentalVariable getEnvironmentalVariable() {
        return environmentalVariable;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }
}
