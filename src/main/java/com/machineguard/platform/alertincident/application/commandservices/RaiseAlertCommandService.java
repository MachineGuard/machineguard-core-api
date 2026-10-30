package com.machineguard.platform.alertincident.application.commandservices;

import com.machineguard.platform.alertincident.application.ports.AlertIncidentEventPublisher;
import com.machineguard.platform.alertincident.domain.commands.AlertIncidentCommands.RaiseAlertCommand;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.AlertRaised;
import com.machineguard.platform.alertincident.domain.model.Alert;
import com.machineguard.platform.alertincident.domain.model.AlertSeverity;
import com.machineguard.platform.alertincident.domain.model.Incident;
import com.machineguard.platform.alertincident.domain.repositories.AlertRepository;
import com.machineguard.platform.alertincident.domain.repositories.IncidentRepository;
import com.machineguard.platform.alertincident.domain.services.AlertPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Abre el incidente y su alerta a partir de una desviacion. Environmental Monitoring publica DeviationDetected con
 * cada medicion fuera de rango, asi que mientras la zona y la variable tengan un incidente sin resolver las nuevas
 * desviaciones pertenecen a ese mismo caso y no abren otro.
 */
@Service
public class RaiseAlertCommandService {

    private final IncidentRepository incidents;
    private final AlertRepository alerts;
    private final AlertPolicyService policy;
    private final AlertIncidentEventPublisher events;

    public RaiseAlertCommandService(IncidentRepository incidents, AlertRepository alerts, AlertPolicyService policy,
                                    AlertIncidentEventPublisher events) {
        this.incidents = incidents;
        this.alerts = alerts;
        this.policy = policy;
        this.events = events;
    }

    /** Devuelve vacio cuando la desviacion se absorbe en un incidente que ya existe. */
    @Transactional
    public Optional<Alert> handle(RaiseAlertCommand command) {
        if (incidents.findByDeviationId(command.deviationId()).isPresent()) {
            return Optional.empty();
        }
        String openKey = Incident.openKeyOf(command.organizationId(), command.monitoringZoneId(),
                command.environmentalVariable());
        if (incidents.findByOpenKey(openKey).isPresent()) {
            return Optional.empty();
        }
        Incident incident = incidents.save(Incident.open(command.deviationId(), command.organizationId(),
                command.monitoringZoneId(), command.environmentalVariable(), command.detectedAt()));
        AlertSeverity severity = policy.classify(command.measuredValue(), command.minimumValue(), command.maximumValue());
        String message = "%s fuera de rango en la zona %s: medicion %s (rango seguro %s a %s)".formatted(
                command.environmentalVariable() == null ? "Variable" : command.environmentalVariable().name(),
                command.monitoringZoneId(), command.measuredValue().toPlainString(),
                command.minimumValue().toPlainString(), command.maximumValue().toPlainString());
        Alert alert = alerts.save(Alert.raise(incident.getId(), severity, message, command.detectedAt()));
        events.publish(new AlertRaised(alert.getId(), incident.getId(), incident.getOrganizationId(),
                incident.getMonitoringZoneId(), alert.getSeverity(), alert.getRaisedAt()));
        return Optional.of(alert);
    }
}
