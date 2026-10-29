package com.machineguard.platform.alertincident.infrastructure.integration;

import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.IncidentResolved;
import com.machineguard.platform.traceability.application.integration.InboundEvents;
import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Puente hacia el Published Language de Traceability and Quality: al resolverse un incidente se entrega su accion
 * correctiva para que la excursion correspondiente quede vinculada y cerrada. Va en la misma transaccion que la
 * resolucion, de modo que ambas quedan o no quedan registradas juntas.
 */
@Component
public class TraceabilityIncidentEventAdapter {

    private final ApplicationEventPublisher publisher;

    public TraceabilityIncidentEventAdapter(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @EventListener
    public void on(IncidentResolved event) {
        EnvironmentalVariable variable = event.environmentalVariable() == null ? null
                : EnvironmentalVariable.valueOf(event.environmentalVariable().name());
        publisher.publishEvent(new InboundEvents.IncidentResolved(event.organizationId(), event.incidentId(),
                event.monitoringZoneId(), variable, event.correctiveAction(), event.resolvedAt()));
    }
}
