package com.machineguard.platform.traceability.interfaces.rest.assemblers;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.machineguard.platform.traceability.application.ports.MeasurementHistoryProvider.MeasurementPoint;
import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.model.NonConformity;
import com.machineguard.platform.traceability.domain.model.TraceabilityReport;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.ExcursionDetailResource;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.ExcursionResource;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.MeasurementHistoryResource;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.MeasurementResource;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.NonConformityResource;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.TraceabilityReportResource;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Assemblers: entidades de dominio a Resources REST. */
@Component
public class ResourceAssemblers {

    private final Clock clock;
    private final JsonMapper objectMapper;

    public ResourceAssemblers(Clock clock, JsonMapper objectMapper) {
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public ExcursionResource excursion(Excursion e) {
        return new ExcursionResource(e.getId(), e.getMonitoringZoneId(), e.getMonitoringPointId(),
                e.getEnvironmentalVariable().name(), e.getStartedAt(), e.getEndedAt(),
                e.calculateDuration(clock.instant()).toSeconds(), e.getPeakValue(), e.getThresholdValue(),
                e.getSeverity().name(), e.getStatus().name(), e.getIncidentId(), e.getCorrectiveAction());
    }

    public ExcursionDetailResource excursionDetail(Excursion e, List<NonConformity> ncs) {
        return new ExcursionDetailResource(excursion(e), ncs.stream().map(this::nonConformity).toList());
    }

    public NonConformityResource nonConformity(NonConformity n) {
        return new NonConformityResource(n.getId(), n.getExcursionId(), n.getBatch().getCode(),
                n.getBatch().getProductName(), n.getBatch().getQuantity(), n.getBatch().getStoredAt(),
                n.getClassification(), n.getDisposition().name(), n.getJustification(), n.getRegisteredBy(),
                n.getRegisteredAt(), n.getResolvedAt());
    }

    public TraceabilityReportResource reportSummary(TraceabilityReport r) {
        return report(r, null);
    }

    public TraceabilityReportResource reportWithContent(TraceabilityReport r) {
        try {
            return report(r, r.getContent() == null ? null : objectMapper.readTree(r.getContent()));
        } catch (JacksonException e) {
            throw new IllegalStateException("El contenido del reporte " + r.getId() + " no es JSON valido", e);
        }
    }

    public MeasurementHistoryResource measurementHistory(UUID zoneId, Instant from, Instant to,
                                                         List<MeasurementPoint> points) {
        List<MeasurementResource> items = points.stream()
                .map(p -> new MeasurementResource(p.monitoringPointId(), p.environmentalVariable().name(),
                        p.value(), p.recordedAt()))
                .toList();
        return new MeasurementHistoryResource(zoneId, from, to, items.size(), items);
    }

    private TraceabilityReportResource report(TraceabilityReport r, JsonNode content) {
        return new TraceabilityReportResource(r.getId(), r.getMonitoringZoneId(), r.getPeriodStart(),
                r.getPeriodEnd(), r.getGeneratedBy(), r.getGeneratedAt(), r.getExcursionCount(),
                r.getMeasurementCount(), r.getStatus().name(), r.getChecksum(), r.verifyIntegrity(),
                r.getExcursionIds().stream().sorted().toList(), content);
    }
}
