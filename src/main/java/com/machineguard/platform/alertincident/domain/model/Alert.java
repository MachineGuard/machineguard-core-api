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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Entity del agregado Incident: alerta emitida cuando una medicion sale del rango seguro. */
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false, updatable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;

    @Column(name = "raised_at", nullable = false, updatable = false)
    private Instant raisedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledgement_note", length = 500)
    private String acknowledgementNote;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Lectura que origino la alerta y rango seguro vigente; nulos en alertas anteriores a V12. */
    @Column(name = "measured_value", precision = 6, scale = 2, updatable = false)
    private BigDecimal measuredValue;

    @Column(name = "minimum_value", precision = 6, scale = 2, updatable = false)
    private BigDecimal minimumValue;

    @Column(name = "maximum_value", precision = 6, scale = 2, updatable = false)
    private BigDecimal maximumValue;

    protected Alert() {
    }

    public static Alert raise(UUID incidentId, AlertSeverity severity, String message, Instant raisedAt) {
        Alert alert = new Alert();
        alert.incidentId = Objects.requireNonNull(incidentId);
        alert.severity = Objects.requireNonNull(severity);
        Objects.requireNonNull(message);
        if (message.isBlank() || message.length() > 500)
            throw new IllegalArgumentException("Alert message must contain text and at most 500 characters");
        alert.message = message;
        alert.status = AlertStatus.PENDING;
        alert.raisedAt = Objects.requireNonNull(raisedAt);
        return alert;
    }

    /** Igual que {@link #raise(UUID, AlertSeverity, String, Instant)}, conservando la lectura y su rango seguro. */
    public static Alert raise(UUID incidentId, AlertSeverity severity, String message, Instant raisedAt,
                              BigDecimal measuredValue, BigDecimal minimumValue, BigDecimal maximumValue) {
        Alert alert = raise(incidentId, severity, message, raisedAt);
        alert.measuredValue = Objects.requireNonNull(measuredValue);
        alert.minimumValue = Objects.requireNonNull(minimumValue);
        alert.maximumValue = Objects.requireNonNull(maximumValue);
        return alert;
    }

    /** Solo se reconoce una alerta que sigue abierta y que nadie ha reconocido antes. */
    public void acknowledge(UUID acknowledgedBy, String note, Instant at) {
        if (status == AlertStatus.CLOSED) {
            throw new AlertIncidentRuleException("La alerta " + id + " ya esta cerrada y no puede reconocerse");
        }
        if (acknowledgedAt != null) {
            throw new AlertIncidentRuleException("La alerta " + id + " ya fue reconocida");
        }
        this.status = AlertStatus.ACKNOWLEDGED;
        this.acknowledgedBy = Objects.requireNonNull(acknowledgedBy);
        this.acknowledgementNote = note == null || note.isBlank() ? null : note.trim();
        this.acknowledgedAt = at.truncatedTo(ChronoUnit.MILLIS);
    }

    public void close(Instant at) {
        this.status = AlertStatus.CLOSED;
        this.closedAt = at.truncatedTo(ChronoUnit.MILLIS);
    }

    public boolean isAcknowledged() {
        return acknowledgedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public UUID getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public String getAcknowledgementNote() {
        return acknowledgementNote;
    }

    public Instant getEscalatedAt() {
        return escalatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public BigDecimal getMeasuredValue() {
        return measuredValue;
    }

    public BigDecimal getMinimumValue() {
        return minimumValue;
    }

    public BigDecimal getMaximumValue() {
        return maximumValue;
    }
}
