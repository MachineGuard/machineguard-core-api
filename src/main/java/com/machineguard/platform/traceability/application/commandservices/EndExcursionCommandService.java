package com.machineguard.platform.traceability.application.commandservices;

import com.machineguard.platform.traceability.domain.commands.EndExcursionCommand;
import com.machineguard.platform.traceability.domain.events.TraceabilityEvents.ExcursionEnded;
import com.machineguard.platform.traceability.domain.exceptions.ResourceNotFoundException;
import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.repositories.ExcursionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndExcursionCommandService {

    private final ExcursionRepository excursions;
    private final ApplicationEventPublisher events;

    public EndExcursionCommandService(ExcursionRepository excursions, ApplicationEventPublisher events) {
        this.excursions = excursions;
        this.events = events;
    }

    @Transactional
    public Excursion handle(EndExcursionCommand command) {
        Excursion excursion = excursions.findByIdAndOrganizationId(command.excursionId(), command.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Excursion " + command.excursionId() + " no encontrada"));
        excursion.end(command.endedAt());
        excursions.save(excursion);
        events.publishEvent(new ExcursionEnded(excursion.getId(), excursion.getOrganizationId(),
                excursion.getMonitoringZoneId(), excursion.getEndedAt(),
                excursion.calculateDuration(excursion.getEndedAt()).toSeconds(), excursion.getPeakValue()));
        return excursion;
    }
}
