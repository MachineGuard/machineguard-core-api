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
import java.util.UUID;

/** Resources / DTOs de la Interface Layer de Traceability & Quality. */
public final class TraceabilityResources {

    private TraceabilityResources() {
    }

    public record ExcursionResource(UUID id, UUID monitoringZoneId, UUID monitoringPointId,
                                    String environmentalVariable, Instant startedAt, Instant endedAt,
                                    long durationSeconds, double peakValue, double thresholdValue, String severity,
                                    String status, UUID incidentId, String correctiveAction) {
    }

    public record ExcursionDetailResource(ExcursionResource excursion, List<NonConformityResource> nonConformities) {
    }

    public record NonConformityResource(UUID id, UUID excursionId, String batchCode, String productName,
                                        BigDecimal quantity, Instant storedAt, String classification,
                                        String disposition, String justification, UUID registeredBy,
                                        Instant registeredAt, Instant resolvedAt) {
    }

    public record RegisterNonConformityResource(@NotBlank @Size(max = 60) String batchCode,
                                                @Size(max = 150) String productName,
                                                @Positive BigDecimal quantity, Instant storedAt,
                                                @NotBlank @Size(max = 60) String classification) {
    }

    public record UpdateDispositionResource(@NotNull Disposition disposition, @Size(max = 1000) String justification) {
    }

    public record GenerateTraceabilityReportResource(@NotNull UUID monitoringZoneId, @NotNull Instant periodStart,
                                                     @NotNull Instant periodEnd) {
    }

    /** content solo se incluye al consultar o descargar un reporte puntual, no en el listado. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TraceabilityReportResource(UUID id, UUID monitoringZoneId, Instant periodStart, Instant periodEnd,
                                             UUID generatedBy, Instant generatedAt, int excursionCount,
                                             int measurementCount, String status, String checksum,
                                             boolean integrityVerified, List<UUID> excursionIds, JsonNode content) {
    }

    public record MeasurementResource(UUID monitoringPointId, String environmentalVariable, double value,
                                      Instant recordedAt) {
    }

    public record MeasurementHistoryResource(UUID monitoringZoneId, Instant from, Instant to, int count,
                                             List<MeasurementResource> measurements) {
    }
}
