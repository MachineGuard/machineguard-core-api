package com.machineguard.platform.traceability.domain.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machineguard.platform.traceability.application.ports.MeasurementHistoryProvider.MeasurementPoint;
import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.model.NonConformity;
import com.machineguard.platform.traceability.domain.model.TraceabilityReport;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consolida la evidencia de un periodo (Measurement History, excursiones cerradas y acciones correctivas)
 * en un snapshot JSON y sella el reporte para garantizar su inmutabilidad.
 */
@Component
public class TraceabilityReportAssemblyService {

    private final ObjectMapper objectMapper;

    public TraceabilityReportAssemblyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void assembleAndSeal(TraceabilityReport report, List<Excursion> closedExcursions,
                                List<NonConformity> nonConformities, List<MeasurementPoint> measurements,
                                int ongoingExcursionsExcluded) {
        closedExcursions.forEach(report::includeExcursion);
        report.includeMeasurementHistory(measurements.size());
        report.seal(serialize(buildContent(closedExcursions, nonConformities, measurements, ongoingExcursionsExcluded)));
    }

    private Content buildContent(List<Excursion> excursions, List<NonConformity> nonConformities,
                                 List<MeasurementPoint> measurements, int ongoingExcluded) {
        Map<Long, List<NonConformity>> byExcursion =
                nonConformities.stream().collect(Collectors.groupingBy(NonConformity::getExcursionId));

        List<ExcursionEntry> excursionEntries = excursions.stream()
                .map(e -> new ExcursionEntry(
                        e.getId(), e.getEnvironmentalVariable().name(), e.getMonitoringPointId(),
                        e.getStartedAt(), e.getEndedAt(), e.calculateDuration(e.getEndedAt()).toSeconds(),
                        e.getPeakValue(), e.getThresholdValue(), e.getSeverity().name(), e.getIncidentId(),
                        byExcursion.getOrDefault(e.getId(), List.of()).stream()
                                .map(nc -> new NonConformityEntry(nc.getBatch().getCode(), nc.getBatch().getProductName(),
                                        nc.getClassification(), nc.getDisposition().name(), nc.getJustification()))
                                .toList()))
                .toList();

        List<CorrectiveActionEntry> actions = excursions.stream()
                .filter(e -> e.getIncidentId() != null)
                .map(e -> new CorrectiveActionEntry(e.getId(), e.getIncidentId(), e.getCorrectiveAction()))
                .toList();

        List<MeasurementEntry> points = measurements.stream()
                .map(m -> new MeasurementEntry(m.monitoringPointId(), m.environmentalVariable().name(), m.value(), m.recordedAt()))
                .toList();

        return new Content(excursionEntries, actions, ongoingExcluded, points);
    }

    private String serialize(Content content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el contenido del reporte", e);
        }
    }

    public record Content(List<ExcursionEntry> excursions, List<CorrectiveActionEntry> correctiveActions,
                          int ongoingExcursionsExcluded, List<MeasurementEntry> measurementHistory) {
    }

    public record ExcursionEntry(Long id, String environmentalVariable, Long monitoringPointId, Instant startedAt,
                                 Instant endedAt, long durationSeconds, double peakValue, double thresholdValue,
                                 String severity, Long incidentId, List<NonConformityEntry> nonConformities) {
    }

    public record NonConformityEntry(String batchCode, String productName, String classification,
                                     String disposition, String justification) {
    }

    public record CorrectiveActionEntry(Long excursionId, Long incidentId, String correctiveAction) {
    }

    public record MeasurementEntry(Long monitoringPointId, String environmentalVariable, double value,
                                   Instant recordedAt) {
    }
}
