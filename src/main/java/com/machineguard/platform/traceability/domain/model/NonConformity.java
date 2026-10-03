package com.machineguard.platform.traceability.domain.model;

import com.machineguard.platform.traceability.domain.exceptions.DomainRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Aggregate Root: registro formal de un lote afectado por una excursion y la decision tomada sobre el. */
@Entity
@Table(name = "non_conformities")
public class NonConformity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "excursion_id", nullable = false)
    private UUID excursionId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Embedded
    private Batch batch;

    @Column(nullable = false, length = 60)
    private String classification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Disposition disposition;

    @Column(length = 1000)
    private String justification;

    @Column(name = "registered_by", nullable = false)
    private UUID registeredBy;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected NonConformity() {
    }

    /** Registra el lote en cuarentena hasta que el Encargado de Control de Calidad decida su destino. */
    public static NonConformity register(UUID excursionId, UUID organizationId, Batch batch, String classification,
                                         UUID registeredBy, Instant now) {
        if (classification == null || classification.isBlank()) {
            throw new IllegalArgumentException("La clasificacion de la no conformidad es obligatoria");
        }
        NonConformity nc = new NonConformity();
        nc.excursionId = excursionId;
        nc.organizationId = organizationId;
        nc.batch = batch;
        nc.classification = classification.trim();
        nc.disposition = Disposition.QUARANTINED;
        nc.registeredBy = registeredBy;
        nc.registeredAt = now.truncatedTo(ChronoUnit.MILLIS);
        return nc;
    }

    public void classify(String classification) {
        requireUnresolved();
        if (classification == null || classification.isBlank()) {
            throw new IllegalArgumentException("La clasificacion es obligatoria");
        }
        this.classification = classification.trim();
    }

    public void quarantine() {
        requireUnresolved();
        disposition = Disposition.QUARANTINED;
    }

    /** Un lote no conforme no puede liberarse sin justificacion registrada. */
    public void release(String justification, Instant now) {
        requireUnresolved();
        if (justification == null || justification.isBlank()) {
            throw new DomainRuleViolationException("Liberar un lote no conforme requiere una justificacion");
        }
        resolve(Disposition.RELEASED, justification.trim(), now);
    }

    public void discard(String justification, Instant now) {
        requireUnresolved();
        resolve(Disposition.DISCARDED, justification == null ? null : justification.trim(), now);
    }

    public boolean isResolved() {
        return resolvedAt != null;
    }

    private void resolve(Disposition disposition, String justification, Instant now) {
        this.disposition = disposition;
        this.justification = justification;
        this.resolvedAt = now.truncatedTo(ChronoUnit.MILLIS);
    }

    private void requireUnresolved() {
        if (isResolved()) {
            throw new DomainRuleViolationException(
                    "La no conformidad " + id + " ya fue resuelta como " + disposition + " y no admite cambios");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getExcursionId() {
        return excursionId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public Batch getBatch() {
        return batch;
    }

    public String getClassification() {
        return classification;
    }

    public Disposition getDisposition() {
        return disposition;
    }

    public String getJustification() {
        return justification;
    }

    public UUID getRegisteredBy() {
        return registeredBy;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
