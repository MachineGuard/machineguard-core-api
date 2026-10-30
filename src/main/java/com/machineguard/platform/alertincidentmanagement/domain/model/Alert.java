package com.machineguard.platform.alertincidentmanagement.domain.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "alerts")
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "incident_id", nullable = false, unique = true)
    private UUID incidentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private AlertSeverity severity;
    @Column(name = "message", nullable = false, length = 500)
    private String message;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlertStatus status;
    @Column(name = "raised_at", nullable = false)
    private OffsetDateTime raisedAt;
    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;
    @Column(name = "escalated_at")
    private OffsetDateTime escalatedAt;

    protected Alert() {}

    /** The caller supplies severity; classification and incident lifecycle are separate responsibilities. */
    public static Alert raise(UUID incidentId, AlertSeverity severity, String message, OffsetDateTime raisedAt) {
        Alert alert = new Alert();
        alert.incidentId = Objects.requireNonNull(incidentId);
        alert.severity = Objects.requireNonNull(severity);
        alert.message = Objects.requireNonNull(message);
        if (message.isBlank() || message.length() > 500)
            throw new IllegalArgumentException("Alert message must contain text and at most 500 characters");
        alert.raisedAt = Objects.requireNonNull(raisedAt);
        alert.status = AlertStatus.PENDING;
        return alert;
    }

    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public AlertSeverity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public AlertStatus getStatus() { return status; }
    public OffsetDateTime getRaisedAt() { return raisedAt; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public OffsetDateTime getEscalatedAt() { return escalatedAt; }
}
