package com.machineguard.platform.alertincident.application.commandservices;

import com.machineguard.platform.alertincident.application.ports.AlertIncidentEventPublisher;
import com.machineguard.platform.alertincident.application.ports.EnvironmentalConditionProvider;
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
    private final EnvironmentalConditionProvider environment;

    public ResolveIncidentCommandService(IncidentRepository incidents, AlertRepository alerts,
                                         CorrectiveActionRepository actions, IncidentResolutionService resolution,
                                         AlertIncidentEventPublisher events, Clock clock, EnvironmentalConditionProvider environment) {
        this.incidents = incidents;
        this.alerts = alerts;
        this.actions = actions;
        this.resolution = resolution;
        this.events = events;
        this.clock = clock;
        this.environment = environment;
    }

    /**
     * Exige una accion correctiva y las ultimas mediciones de la zona dentro del rango seguro, cierra su alerta y publica
     * IncidentResolved, que Traceability and Quality consume en la misma transaccion para vincular y cerrar la excursion.
     */
    @Transactional
    public Incident handle(ResolveIncidentCommand command) {
        var zoneId = incidents.findZoneIdByIdAndOrganizationId(command.incidentId(), command.organizationId())
                .orElseThrow(() -> new AlertIncidentNotFoundException("Incident not found"));
        environment.lockZone(command.organizationId(), zoneId);
        Incident incident = incidents.lockByIdAndOrganizationId(command.incidentId(), command.organizationId())
                .orElseThrow(() -> new AlertIncidentNotFoundException("Incidente " + command.incidentId() + " no encontrado"));
        List<CorrectiveAction> performed = actions.findByIncidentId(incident.getId());
        resolution.ensureResolvable(incident, performed, environment.isWithinSafeRange(command.organizationId(),
                zoneId, incident.getEnvironmentalVariable()));

        Instant now = clock.instant();
        incident.resolve(command.resolvedBy(), command.notes(), now);
        incidents.save(incident);
        alerts.findByIncidentId(incident.getId()).ifPresent(alert -> {
            alert.close(now);
            alerts.save(alert);
        });
        events.publish(new IncidentResolved(incident.getId(), incident.getOriginMeasurementId(), incident.getOrganizationId(),
                incident.getMonitoringZoneId(), incident.getEnvironmentalVariable(), resolution.summarize(performed),
                incident.getResolvedAt(), incident.getOpenedAt()));
        return incident;
    }
}
