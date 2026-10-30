package com.machineguard.platform.alertincident.application.eventhandlers;

import com.machineguard.platform.alertincident.application.commandservices.RaiseAlertCommandService;
import com.machineguard.platform.alertincident.domain.commands.AlertIncidentCommands.RaiseAlertCommand;
import com.machineguard.platform.alertincident.domain.model.EnvironmentalVariable;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.DeviationDetected;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Measurement, incident, alert and traceability commit or roll back together. */
@Component
public class IncidentOpeningEventHandler {
    private final RaiseAlertCommandService raiseAlert;
    public IncidentOpeningEventHandler(RaiseAlertCommandService raiseAlert) { this.raiseAlert = raiseAlert; }
    @EventListener
    @Transactional
    public void on(DeviationDetected event) {
        raiseAlert.handle(new RaiseAlertCommand(event.measurementId(), event.organizationId(),
                event.monitoringZoneId(), EnvironmentalVariable.valueOf(event.environmentalVariable().name()),
                event.measuredValue(), event.minimumValue(), event.maximumValue(), event.recordedAt().toInstant()));
    }
}
