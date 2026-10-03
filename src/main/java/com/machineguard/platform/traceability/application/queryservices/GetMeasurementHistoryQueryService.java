package com.machineguard.platform.traceability.application.queryservices;

import com.machineguard.platform.traceability.application.ports.MeasurementHistoryProvider;
import com.machineguard.platform.traceability.application.ports.MeasurementHistoryProvider.MeasurementPoint;
import com.machineguard.platform.traceability.domain.exceptions.ResourceNotFoundException;
import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.queries.GetMeasurementHistoryQuery;
import com.machineguard.platform.traceability.domain.repositories.ExcursionRepository;
import com.machineguard.platform.traceability.infrastructure.config.TraceabilityProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetMeasurementHistoryQueryService {

    private final MeasurementHistoryProvider provider;
    private final ExcursionRepository excursions;
    private final TraceabilityProperties properties;
    private final Clock clock;

    public GetMeasurementHistoryQueryService(MeasurementHistoryProvider provider, ExcursionRepository excursions,
                                             TraceabilityProperties properties, Clock clock) {
        this.provider = provider;
        this.excursions = excursions;
        this.properties = properties;
        this.clock = clock;
    }

    public List<MeasurementPoint> handle(GetMeasurementHistoryQuery query) {
        if (query.from() == null || query.to() == null || !query.from().isBefore(query.to())) {
            throw new IllegalArgumentException("from y to son obligatorios y from debe ser anterior a to");
        }
        if (Duration.between(query.from(), query.to()).toDays() > properties.maxReportDays()) {
            throw new IllegalArgumentException("El rango no puede superar " + properties.maxReportDays() + " dias");
        }
        return provider.findByZone(query.organizationId(), query.monitoringZoneId(), query.variable(),
                query.from(), query.to());
    }

    /** Historial de la zona y variable de una excursion, entre su inicio y su cierre (o ahora si sigue abierta). */
    public List<MeasurementPoint> handleForExcursion(Long organizationId, Long excursionId) {
        Excursion e = excursions.findByIdAndOrganizationId(excursionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Excursion " + excursionId + " no encontrada"));
        Instant to = e.getEndedAt() != null ? e.getEndedAt() : clock.instant();
        return provider.findByZone(organizationId, e.getMonitoringZoneId(), e.getEnvironmentalVariable(),
                e.getStartedAt(), to);
    }
}
