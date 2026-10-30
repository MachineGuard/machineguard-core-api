package com.machineguard.platform.alertincident.infrastructure.integration;

import com.machineguard.platform.alertincident.application.ports.AlertIncidentEventPublisher;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.AlertAcknowledged;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.AlertRaised;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.CorrectiveActionRegistered;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.IncidentResolved;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Usa el bus de eventos sincrono de Spring y participa en la transaccion de quien publica. */
@Component
public class SpringAlertIncidentEventPublisher implements AlertIncidentEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringAlertIncidentEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(AlertRaised event) {
        publisher.publishEvent(event);
    }

    @Override
    public void publish(AlertAcknowledged event) {
        publisher.publishEvent(event);
    }

    @Override
    public void publish(CorrectiveActionRegistered event) {
        publisher.publishEvent(event);
    }

    @Override
    public void publish(IncidentResolved event) {
        publisher.publishEvent(event);
    }
}
