package com.machineguard.platform.alertincident.application.commandservices;

import com.machineguard.platform.alertincident.application.ports.AlertIncidentEventPublisher;
import com.machineguard.platform.alertincident.application.ports.EnvironmentalConditionProvider;
import com.machineguard.platform.alertincident.domain.commands.AlertIncidentCommands.RaiseAlertCommand;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.AlertRaised;
import com.machineguard.platform.alertincident.domain.exceptions.AlertIncidentRuleException;
import com.machineguard.platform.alertincident.domain.model.Alert;
import com.machineguard.platform.alertincident.domain.model.Incident;
import com.machineguard.platform.alertincident.domain.repositories.AlertRepository;
import com.machineguard.platform.alertincident.domain.repositories.IncidentRepository;
import com.machineguard.platform.alertincident.domain.repositories.IncidentMeasurementRepository;
import com.machineguard.platform.alertincident.domain.services.AlertPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Creates or reuses one incident per organization/zone/variable and remembers every absorbed measurement. */
@Service
public class RaiseAlertCommandService {
    private final IncidentRepository incidents;
    private final AlertRepository alerts;
    private final IncidentMeasurementRepository measurements;
    private final EnvironmentalConditionProvider environment;
    private final AlertPolicyService policy;
    private final AlertIncidentEventPublisher events;

    public RaiseAlertCommandService(IncidentRepository incidents, AlertRepository alerts,
            IncidentMeasurementRepository measurements, EnvironmentalConditionProvider environment,
            AlertPolicyService policy, AlertIncidentEventPublisher events) {
        this.incidents = incidents;
        this.alerts = alerts;
        this.measurements = measurements;
        this.environment = environment;
        this.policy = policy;
        this.events = events;
    }

    public record IncidentReference(UUID incidentId, UUID alertId, boolean created) { }

    @Transactional
    public IncidentReference handle(RaiseAlertCommand command) {
        environment.lockZone(command.organizationId(), command.monitoringZoneId());
        var processed = measurements.findIncidentId(command.measurementId());
        if (processed.isPresent()) {
            Incident incident = incidents.findByIdAndOrganizationId(processed.get(), command.organizationId())
                    .orElseThrow(() -> new AlertIncidentRuleException("Measurement belongs to another organization"));
            if (!incident.getMonitoringZoneId().equals(command.monitoringZoneId())
                    || incident.getEnvironmentalVariable() != command.environmentalVariable())
                throw new AlertIncidentRuleException("Measurement identity does not match its original incident");
            Alert alert = alerts.findByIncidentId(incident.getId()).orElseThrow();
            return new IncidentReference(incident.getId(), alert.getId(), false);
        }
        String openKey = Incident.openKeyOf(command.organizationId(), command.monitoringZoneId(), command.environmentalVariable());
        Incident incident = incidents.findByOriginMeasurementId(command.measurementId())
                .or(() -> incidents.findByOpenKey(openKey))
                .orElseGet(() -> incidents.save(Incident.open(command.measurementId(), command.organizationId(),
                        command.monitoringZoneId(), command.environmentalVariable(), command.detectedAt(),command.historical())));
        if (!incident.getOrganizationId().equals(command.organizationId())
                || !incident.getMonitoringZoneId().equals(command.monitoringZoneId())
                || incident.getEnvironmentalVariable() != command.environmentalVariable())
            throw new AlertIncidentRuleException("Measurement identity does not match its original incident");
        if (!command.historical()) {
            incident.recordLiveEvidence(command.detectedAt());
            incidents.save(incident);
        }
        var existing = alerts.findByIncidentId(incident.getId());
        Alert alert;
        if (existing.isPresent()) {
            alert = existing.get();
        } else {
            String message = "%s fuera de rango en la zona %s: medicion %s (rango seguro %s a %s)".formatted(
                    command.environmentalVariable(), command.monitoringZoneId(), command.measuredValue().toPlainString(),
                    command.minimumValue().toPlainString(), command.maximumValue().toPlainString());
            if (command.historical()) message="Desviacion historica: "+message;
            alert = alerts.save(Alert.raise(incident.getId(),
                    policy.classify(command.measuredValue(), command.minimumValue(), command.maximumValue()),
                    message, command.detectedAt(), command.measuredValue(), command.minimumValue(),
                    command.maximumValue()));
            events.publish(new AlertRaised(alert.getId(), incident.getId(), incident.getOrganizationId(),
                    incident.getMonitoringZoneId(), alert.getSeverity(), alert.getRaisedAt()));
        }
        measurements.record(command.measurementId(), incident.getId());
        return new IncidentReference(incident.getId(), alert.getId(), existing.isEmpty());
    }
}
