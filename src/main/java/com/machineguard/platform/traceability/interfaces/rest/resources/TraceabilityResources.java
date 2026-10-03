package com.machineguard.platform.traceability.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.machineguard.platform.traceability.domain.model.Disposition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Resources / DTOs de la Interface Layer de Traceability & Quality. */
public final class TraceabilityResources {

    private TraceabilityResources() {
    }

    public record ExcursionResource(Long id, Long monitoringZoneId, Long monitoringPointId,
                                    String environmentalVariable, Instant startedAt, Instant endedAt,
                                    long durationSeconds, double peakValue, double thresholdValue, String severity,
                                    String status, Long incidentId, String correctiveAction) {
    }

    public record ExcursionDetailResource(ExcursionResource excursion, List<NonConformityResource> nonConformities) {
    }

    public record NonConformityResource(Long id, Long excursionId, String batchCode, String productName,
                                        BigDecimal quantity, Instant storedAt, String classification,
                                        String disposition, String justification, Long registeredBy,
                                        Instant registeredAt, Instant resolvedAt) {
    }

    public record RegisterNonConformityResource(@NotBlank @Size(max = 80) String batchCode,
                                                @Size(max = 200) String productName,
                                                @Positive BigDecimal quantity, Instant storedAt,
                                                @NotBlank @Size(max = 80) String classification) {
    }

    public record UpdateDispositionResource(@NotNull Disposition disposition, @Size(max = 1000) String justification) {
    }

    public record GenerateTraceabilityReportResource(@NotNull Long monitoringZoneId, @NotNull Instant periodStart,
                                                     @NotNull Instant periodEnd) {
    }

    /** content solo se incluye al consultar o descargar un reporte puntual, no en el listado. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TraceabilityReportResource(Long id, Long monitoringZoneId, Instant periodStart, Instant periodEnd,
                                             Long generatedBy, Instant generatedAt, int excursionCount,
                                             int measurementCount, String status, String checksum,
                                             boolean integrityVerified, List<Long> excursionIds, JsonNode content) {
    }

    public record MeasurementResource(Long monitoringPointId, String environmentalVariable, double value,
                                      Instant recordedAt) {
    }

    public record MeasurementHistoryResource(Long monitoringZoneId, Instant from, Instant to, int count,
                                             List<MeasurementResource> measurements) {
    }
}
