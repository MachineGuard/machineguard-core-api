package com.machineguard.platform.alertincident.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Entity del agregado Incident: accion ejecutada por el responsable para atender la condicion anomala. */
@Entity
@Table(name = "corrective_actions")
public class CorrectiveAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false, updatable = false)
    private UUID incidentId;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "performed_by", nullable = false, updatable = false)
    private UUID performedBy;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private Instant performedAt;

    @Column(length = 1000)
    private String notes;

    protected CorrectiveAction() {
    }

    public static CorrectiveAction register(UUID incidentId, String description, UUID performedBy, String notes,
                                            Instant performedAt) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("La descripcion de la accion correctiva es obligatoria");
        }
        CorrectiveAction action = new CorrectiveAction();
        action.incidentId = Objects.requireNonNull(incidentId);
        action.description = description.trim();
        action.performedBy = Objects.requireNonNull(performedBy);
        action.notes = notes == null || notes.isBlank() ? null : notes.trim();
        action.performedAt = performedAt.truncatedTo(ChronoUnit.MILLIS);
        return action;
    }

    public void updateNotes(String notes) {
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getDescription() {
        return description;
    }

    public UUID getPerformedBy() {
        return performedBy;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }

    public String getNotes() {
        return notes;
    }
}
