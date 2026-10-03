package com.machineguard.platform.traceability.application.commandservices;

import com.machineguard.platform.traceability.application.ports.MeasurementHistoryProvider;
import com.machineguard.platform.traceability.application.ports.MeasurementHistoryProvider.MeasurementPoint;
import com.machineguard.platform.traceability.domain.commands.GenerateTraceabilityReportCommand;
import com.machineguard.platform.traceability.domain.events.TraceabilityEvents.TraceabilityReportGenerated;
import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.model.ExcursionPeriod;
import com.machineguard.platform.traceability.domain.model.ExcursionStatus;
import com.machineguard.platform.traceability.domain.model.NonConformity;
import com.machineguard.platform.traceability.domain.model.ReportPeriod;
import com.machineguard.platform.traceability.domain.model.TraceabilityReport;
import com.machineguard.platform.traceability.domain.repositories.ExcursionRepository;
import com.machineguard.platform.traceability.domain.repositories.NonConformityRepository;
import com.machineguard.platform.traceability.domain.repositories.TraceabilityReportRepository;
import com.machineguard.platform.traceability.domain.services.TraceabilityReportAssemblyService;
import com.machineguard.platform.traceability.infrastructure.config.TraceabilityProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class GenerateTraceabilityReportCommandService {

    private final ExcursionRepository excursions;
    private final NonConformityRepository nonConformities;
    private final TraceabilityReportRepository reports;
    private final MeasurementHistoryProvider measurementHistory;
    private final TraceabilityReportAssemblyService assembly;
    private final ApplicationEventPublisher events;
    private final TraceabilityProperties properties;
    private final Clock clock;

    public GenerateTraceabilityReportCommandService(ExcursionRepository excursions,
                                                    NonConformityRepository nonConformities,
                                                    TraceabilityReportRepository reports,
                                                    MeasurementHistoryProvider measurementHistory,
                                                    TraceabilityReportAssemblyService assembly,
                                                    ApplicationEventPublisher events,
                                                    TraceabilityProperties properties, Clock clock) {
        this.excursions = excursions;
        this.nonConformities = nonConformities;
        this.reports = reports;
        this.measurementHistory = measurementHistory;
        this.assembly = assembly;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public TraceabilityReport handle(GenerateTraceabilityReportCommand command) {
        Instant now = clock.instant();
        ReportPeriod period = new ReportPeriod(command.periodStart(), command.periodEnd());
        if (!period.isValid(now, properties.maxReportDays())) {
            throw new IllegalArgumentException("Periodo invalido: el inicio debe ser anterior al fin, el fin no puede "
                    + "estar en el futuro y el periodo no puede superar " + properties.maxReportDays() + " dias");
        }

        List<Excursion> closed = excursions.findClosedOverlapping(
                command.organizationId(), command.monitoringZoneId(), period.from(), period.to());
        int ongoingExcluded = (int) excursions
                .findByOrganizationIdAndMonitoringZoneIdAndStatus(command.organizationId(), command.monitoringZoneId(),
                        ExcursionStatus.ONGOING)
                .stream().filter(e -> e.period().overlaps(new ExcursionPeriod(period.from(), period.to())))
                .count();
        List<NonConformity> ncs = closed.isEmpty() ? List.of()
                : nonConformities.findByExcursionIdInOrderByRegisteredAtAsc(closed.stream().map(Excursion::getId).toList());
        List<MeasurementPoint> measurements = measurementHistory.findByZone(
                command.organizationId(), command.monitoringZoneId(), null, period.from(), period.to());

        TraceabilityReport report = TraceabilityReport.generate(
                command.organizationId(), command.monitoringZoneId(), period, command.generatedBy(), now);
        assembly.assembleAndSeal(report, closed, ncs, measurements, ongoingExcluded);
        TraceabilityReport saved = reports.save(report);

        events.publishEvent(new TraceabilityReportGenerated(saved.getId(), saved.getOrganizationId(),
                saved.getMonitoringZoneId(), saved.getGeneratedAt(), saved.getChecksum()));
        return saved;
    }
}
