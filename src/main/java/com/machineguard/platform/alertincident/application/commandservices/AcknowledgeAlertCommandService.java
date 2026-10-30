package com.machineguard.platform.alertincident.application.commandservices;

import com.machineguard.platform.alertincident.application.ports.AlertIncidentEventPublisher;
import com.machineguard.platform.alertincident.domain.commands.AlertIncidentCommands.AcknowledgeAlertCommand;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.AlertAcknowledged;
import com.machineguard.platform.alertincident.domain.exceptions.AlertIncidentNotFoundException;
import com.machineguard.platform.alertincident.domain.model.Alert;
import com.machineguard.platform.alertincident.domain.model.Incident;
import com.machineguard.platform.alertincident.domain.repositories.AlertRepository;
import com.machineguard.platform.alertincident.domain.repositories.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AcknowledgeAlertCommandService {

    private final AlertRepository alerts;
    private final IncidentRepository incidents;
    private final AlertIncidentEventPublisher events;
    private final Clock clock;

    public AcknowledgeAlertCommandService(AlertRepository alerts, IncidentRepository incidents,
                                          AlertIncidentEventPublisher events, Clock clock) {
        this.alerts = alerts;
        this.incidents = incidents;
        this.events = events;
        this.clock = clock;
    }

    public record Acknowledgement(Alert alert, Incident incident) {
    }

    /**
     * Registra el reconocimiento y actualiza el ciclo de atencion de la alerta y del incidente. La alerta se busca a
     * traves del incidente de la organizacion, de modo que una alerta ajena responde como inexistente.
     */
    @Transactional
    public Acknowledgement handle(AcknowledgeAlertCommand command) {
        var incidentId = alerts.findIncidentIdById(command.alertId())
                .orElseThrow(() -> new AlertIncidentNotFoundException("Alerta " + command.alertId() + " no encontrada"));
        Incident incident = incidents.lockByIdAndOrganizationId(incidentId, command.organizationId())
                .orElseThrow(() -> new AlertIncidentNotFoundException("Alerta " + command.alertId() + " no encontrada"));
        Alert alert = alerts.findById(command.alertId()).orElseThrow();
        var now = clock.instant();
        alert.acknowledge(command.acknowledgedBy(), command.note(), now);
        incident.acknowledge();
        alerts.save(alert);
        incidents.save(incident);
        events.publish(new AlertAcknowledged(alert.getId(), incident.getId(), incident.getOrganizationId(),
                alert.getAcknowledgedBy(), alert.getAcknowledgedAt()));
        return new Acknowledgement(alert, incident);
    }
}
