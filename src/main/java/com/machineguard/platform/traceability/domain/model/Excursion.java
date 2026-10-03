package com.machineguard.platform.traceability.domain.model;

import com.machineguard.platform.traceability.domain.exceptions.DomainRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Aggregate Root: periodo continuo en que una zona permanecio fuera de su Safe Range. */
@Entity
@Table(name = "excursions")
public class Excursion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "monitoring_zone_id", nullable = false)
    private Long monitoringZoneId;

    @Column(name = "monitoring_point_id")
    private Long monitoringPointId;

    @Enumerated(EnumType.STRING)
    @Column(name = "environmental_variable", nullable = false, length = 20)
    private EnvironmentalVariable environmentalVariable;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "peak_value", nullable = false)
    private double peakValue;

    @Column(name = "threshold_value", nullable = false)
    private double thresholdValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExcursionSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExcursionStatus status;

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "corrective_action", length = 1000)
    private String correctiveAction;

    @Column(name = "open_key", length = 100)
    private String openKey;

    protected Excursion() {
    }

    public static Excursion start(Long organizationId, Long monitoringZoneId, Long monitoringPointId,
                                  EnvironmentalVariable variable, double detectedValue, double thresholdValue,
                                  Instant detectedAt) {
        Excursion e = new Excursion();
        e.organizationId = organizationId;
        e.monitoringZoneId = monitoringZoneId;
        e.monitoringPointId = monitoringPointId;
        e.environmentalVariable = variable;
        e.startedAt = detectedAt.truncatedTo(ChronoUnit.MILLIS);
        e.peakValue = detectedValue;
        e.thresholdValue = thresholdValue;
        e.severity = ExcursionSeverity.classify(e.magnitude(), Duration.ZERO);
        e.status = ExcursionStatus.ONGOING;
        e.openKey = openKeyOf(organizationId, monitoringZoneId, variable);
        return e;
    }

    public static String openKeyOf(Long organizationId, Long monitoringZoneId, EnvironmentalVariable variable) {
        return organizationId + ":" + monitoringZoneId + ":" + variable;
    }

    /** Una nueva desviacion sobre la excursion abierta solo actualiza el valor pico y la severidad. */
    public void registerDeviation(double value, Instant at) {
        requireOngoing("registrar una desviacion");
        updatePeakValue(value);
        severity = severity.max(ExcursionSeverity.classify(magnitude(), Duration.between(startedAt, at)));
    }

    public void updatePeakValue(double value) {
        if (Math.abs(value - thresholdValue) > Math.abs(peakValue - thresholdValue)) {
            peakValue = value;
        }
    }

    public void end(Instant at) {
        requireOngoing("cerrarla");
        Instant closedAt = at.truncatedTo(ChronoUnit.MILLIS);
        if (closedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("El cierre no puede ser anterior al inicio de la excursion");
        }
        endedAt = closedAt;
        status = ExcursionStatus.CLOSED;
        openKey = null;
        severity = severity.max(ExcursionSeverity.classify(magnitude(), Duration.between(startedAt, closedAt)));
    }

    /** Vincula el incidente resuelto; el primer vinculo prevalece. Devuelve true si se vinculo ahora. */
    public boolean linkIncident(Long incidentId, String correctiveAction) {
        if (this.incidentId != null) {
            return false;
        }
        this.incidentId = incidentId;
        this.correctiveAction = correctiveAction;
        return true;
    }

    public Duration calculateDuration(Instant now) {
        return period().duration(now);
    }

    public ExcursionPeriod period() {
        return new ExcursionPeriod(startedAt, endedAt);
    }

    public boolean isOngoing() {
        return status == ExcursionStatus.ONGOING;
    }

    private double magnitude() {
        return Math.abs(peakValue - thresholdValue);
    }

    private void requireOngoing(String action) {
        if (!isOngoing()) {
            throw new DomainRuleViolationException("La excursion " + id + " ya esta cerrada: no se puede " + action);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getMonitoringZoneId() {
        return monitoringZoneId;
    }

    public Long getMonitoringPointId() {
        return monitoringPointId;
    }

    public EnvironmentalVariable getEnvironmentalVariable() {
        return environmentalVariable;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public double getPeakValue() {
        return peakValue;
    }

    public double getThresholdValue() {
        return thresholdValue;
    }

    public ExcursionSeverity getSeverity() {
        return severity;
    }

    public ExcursionStatus getStatus() {
        return status;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getCorrectiveAction() {
        return correctiveAction;
    }
}
