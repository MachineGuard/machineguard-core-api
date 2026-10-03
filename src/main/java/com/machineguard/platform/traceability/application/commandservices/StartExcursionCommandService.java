package com.machineguard.platform.traceability.application.commandservices;

import com.machineguard.platform.traceability.domain.commands.StartExcursionCommand;
import com.machineguard.platform.traceability.domain.events.TraceabilityEvents.ExcursionStarted;
import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.repositories.ExcursionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StartExcursionCommandService {

    private final ExcursionRepository excursions;
    private final ApplicationEventPublisher events;

    public StartExcursionCommandService(ExcursionRepository excursions, ApplicationEventPublisher events) {
        this.excursions = excursions;
        this.events = events;
    }

    @Transactional
    public Excursion handle(StartExcursionCommand command) {
        Excursion excursion = excursions.saveAndFlush(Excursion.start(
                command.organizationId(), command.monitoringZoneId(), command.monitoringPointId(),
                command.environmentalVariable(), command.detectedValue(), command.thresholdValue(),
                command.detectedAt()));
        events.publishEvent(new ExcursionStarted(excursion.getId(), excursion.getOrganizationId(),
                excursion.getMonitoringZoneId(), excursion.getEnvironmentalVariable(), excursion.getStartedAt()));
        return excursion;
    }
}
