package com.machineguard.platform.alertincident.application.commandservices;

import com.machineguard.platform.alertincident.application.ports.AlertIncidentEventPublisher;
import com.machineguard.platform.alertincident.domain.commands.AlertIncidentCommands.RegisterCorrectiveActionCommand;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.CorrectiveActionRegistered;
import com.machineguard.platform.alertincident.domain.exceptions.AlertIncidentNotFoundException;
import com.machineguard.platform.alertincident.domain.model.CorrectiveAction;
import com.machineguard.platform.alertincident.domain.model.Incident;
import com.machineguard.platform.alertincident.domain.repositories.CorrectiveActionRepository;
import com.machineguard.platform.alertincident.domain.repositories.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class RegisterCorrectiveActionCommandService {

    private final IncidentRepository incidents;
    private final CorrectiveActionRepository actions;
    private final AlertIncidentEventPublisher events;
    private final Clock clock;

    public RegisterCorrectiveActionCommandService(IncidentRepository incidents, CorrectiveActionRepository actions,
                                                  AlertIncidentEventPublisher events, Clock clock) {
        this.incidents = incidents;
        this.actions = actions;
        this.events = events;
        this.clock = clock;
    }

    /** Toda accion correctiva queda asociada al incidente que la origino, siempre que este no este resuelto. */
    @Transactional
    public CorrectiveAction handle(RegisterCorrectiveActionCommand command) {
        Incident incident = incidents.lockByIdAndOrganizationId(command.incidentId(), command.organizationId())
                .orElseThrow(() -> new AlertIncidentNotFoundException("Incidente " + command.incidentId() + " no encontrado"));
        incident.requireUnresolved("registrar una accion correctiva");
        CorrectiveAction action = actions.save(CorrectiveAction.register(incident.getId(), command.description(),
                command.performedBy(), command.notes(), clock.instant()));
        events.publish(new CorrectiveActionRegistered(action.getId(), incident.getId(), incident.getOrganizationId(),
                action.getPerformedBy(), action.getPerformedAt()));
        return action;
    }
}
