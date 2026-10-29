package com.machineguard.platform.alertincident.application.eventhandlers;

import com.machineguard.platform.alertincident.application.commandservices.RaiseAlertCommandService;
import com.machineguard.platform.alertincident.domain.commands.AlertIncidentCommands.RaiseAlertCommand;
import com.machineguard.platform.alertincident.domain.model.EnvironmentalVariable;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.DeviationDetected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacciona a las desviaciones de Environmental Monitoring. Se ejecuta cuando la medicion ya quedo confirmada y en
 * su propia transaccion: un fallo al abrir el incidente no puede impedir que se registre la medicion.
 */
@Component
public class IncidentOpeningEventHandler {

    private static final Logger log = LoggerFactory.getLogger(IncidentOpeningEventHandler.class);

    private final RaiseAlertCommandService raiseAlert;

    public IncidentOpeningEventHandler(RaiseAlertCommandService raiseAlert) {
        this.raiseAlert = raiseAlert;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(DeviationDetected event) {
        try {
            raiseAlert.handle(new RaiseAlertCommand(event.measurementId(), event.organizationId(),
                    event.monitoringZoneId(), EnvironmentalVariable.valueOf(event.environmentalVariable().name()),
                    event.measuredValue(), event.minimumValue(), event.maximumValue(), event.recordedAt().toInstant()));
        } catch (RuntimeException e) {
            log.error("No se pudo abrir el incidente de la desviacion {}", event.measurementId(), e);
        }
    }
}
