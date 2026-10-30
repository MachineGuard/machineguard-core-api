package com.machineguard.platform.alertincident.application.commandservices;

import com.machineguard.platform.alertincident.application.ports.AlertIncidentEventPublisher;
import com.machineguard.platform.alertincident.domain.commands.AlertIncidentCommands.ResolveIncidentCommand;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.IncidentResolved;
import com.machineguard.platform.alertincident.domain.exceptions.AlertIncidentNotFoundException;
import com.machineguard.platform.alertincident.domain.model.CorrectiveAction;
import com.machineguard.platform.alertincident.domain.model.Incident;
import com.machineguard.platform.alertincident.domain.repositories.AlertRepository;
import com.machineguard.platform.alertincident.domain.repositories.CorrectiveActionRepository;
import com.machineguard.platform.alertincident.domain.repositories.IncidentRepository;
import com.machineguard.platform.alertincident.domain.services.IncidentResolutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ResolveIncidentCommandService {

    private final IncidentRepository incidents;
    private final AlertRepository alerts;
    private final CorrectiveActionRepository actions;
    private final IncidentResolutionService resolution;
    private final AlertIncidentEventPublisher events;
    private final Clock clock;

    public ResolveIncidentCommandService(IncidentRepository incidents, AlertRepository alerts,
                                         CorrectiveActionRepository actions, IncidentResolutionService resolution,
                                         AlertIncidentEventPublisher events, Clock clock) {
        this.incidents = incidents;
        this.alerts = alerts;
        this.actions = actions;
        this.resolution = resolution;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Cierra formalmente el incidente (exige al menos una accion correctiva), cierra su alerta y publica
     * IncidentResolved, que Traceability and Quality consume en la misma transaccion para vincular y cerrar la excursion.
     */
    @Transactional
    public Incident handle(ResolveIncidentCommand command) {
        Incident incident = incidents.findByIdAndOrganizationId(command.incidentId(), command.organizationId())
                .orElseThrow(() -> new AlertIncidentNotFoundException("Incidente " + command.incidentId() + " no encontrado"));
        List<CorrectiveAction> performed = actions.findByIncidentId(incident.getId());
        resolution.ensureResolvable(incident, performed);

        Instant now = clock.instant();
        incident.resolve(command.resolvedBy(), command.notes(), now);
        incidents.save(incident);
        alerts.findByIncidentId(incident.getId()).ifPresent(alert -> {
            alert.close(now);
            alerts.save(alert);
        });
        events.publish(new IncidentResolved(incident.getId(), incident.getOriginMeasurementId(), incident.getOrganizationId(),
                incident.getMonitoringZoneId(), incident.getEnvironmentalVariable(), resolution.summarize(performed),
                incident.getResolvedAt()));
        return incident;
    }
}
