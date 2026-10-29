package com.machineguard.platform.alertincident.application.ports;

import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.AlertAcknowledged;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.AlertRaised;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.CorrectiveActionRegistered;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.IncidentResolved;

/** Publica los eventos del contexto sin exponer el transporte a los servicios de aplicacion. */
public interface AlertIncidentEventPublisher {

    void publish(AlertRaised event);

    void publish(AlertAcknowledged event);

    void publish(CorrectiveActionRegistered event);

    void publish(IncidentResolved event);
}
