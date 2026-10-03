package com.machineguard.platform.traceability.domain.model;

import com.machineguard.platform.traceability.domain.exceptions.DomainRuleViolationException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Aggregate Root: documento de evidencia para auditorias. Se persiste siempre ya sellado y es inmutable:
 * el checksum SHA-256 permite verificar que su contenido no cambio.
 */
@Entity
@Immutable
@Table(name = "traceability_reports")
public class TraceabilityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "monitoring_zone_id", nullable = false)
    private Long monitoringZoneId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "generated_by", nullable = false)
    private Long generatedBy;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "excursion_count", nullable = false)
    private int excursionCount;

    @Column(name = "measurement_count", nullable = false)
    private int measurementCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportStatus status;

    @Column(length = 64)
    private String checksum;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "traceability_report_excursions", joinColumns = @JoinColumn(name = "traceability_report_id"))
    @Column(name = "excursion_id")
    private Set<Long> excursionIds = new LinkedHashSet<>();

    protected TraceabilityReport() {
    }

    public static TraceabilityReport generate(Long organizationId, Long monitoringZoneId, ReportPeriod period,
                                              Long generatedBy, Instant now) {
        TraceabilityReport r = new TraceabilityReport();
        r.organizationId = organizationId;
        r.monitoringZoneId = monitoringZoneId;
        r.periodStart = period.from();
        r.periodEnd = period.to();
        r.generatedBy = generatedBy;
        r.generatedAt = now.truncatedTo(ChronoUnit.MILLIS);
        r.status = ReportStatus.DRAFT;
        return r;
    }

    /** Una excursion abierta no puede incluirse como evidencia cerrada. */
    public void includeExcursion(Excursion excursion) {
        requireDraft();
        if (excursion.isOngoing()) {
            throw new DomainRuleViolationException(
                    "La excursion " + excursion.getId() + " sigue abierta y no puede incluirse como evidencia cerrada");
        }
        if (excursionIds.add(excursion.getId())) {
            excursionCount = excursionIds.size();
        }
    }

    public void includeMeasurementHistory(int measurementCount) {
        requireDraft();
        this.measurementCount = measurementCount;
    }

    public void seal(String content) {
        requireDraft();
        this.content = content;
        this.status = ReportStatus.SEALED;
        this.checksum = computeChecksum();
    }

    public boolean isSealed() {
        return status == ReportStatus.SEALED;
    }

    /** Recalcula el checksum sobre los datos persistidos y lo compara con el sellado. */
    public boolean verifyIntegrity() {
        return isSealed() && checksum != null && checksum.equals(computeChecksum());
    }

    private String computeChecksum() {
        String canonical = String.join("|",
                String.valueOf(organizationId), String.valueOf(monitoringZoneId),
                String.valueOf(periodStart), String.valueOf(periodEnd),
                String.valueOf(generatedBy), String.valueOf(generatedAt),
                String.valueOf(excursionCount), String.valueOf(measurementCount),
                String.valueOf(excursionIds.stream().sorted().toList()),
                content == null ? "" : content);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private void requireDraft() {
        if (isSealed()) {
            throw new DomainRuleViolationException("El reporte " + id + " esta sellado y es inmutable");
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

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public Long getGeneratedBy() {
        return generatedBy;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public int getExcursionCount() {
        return excursionCount;
    }

    public int getMeasurementCount() {
        return measurementCount;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getContent() {
        return content;
    }

    public Set<Long> getExcursionIds() {
        return Set.copyOf(excursionIds);
    }
}
