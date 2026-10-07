package com.machineguard.platform.environmentalmonitoring.infrastructure.integration;

import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.*;
import com.machineguard.platform.traceability.application.integration.InboundEvents;
import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Compatibility bridge for Traceability's existing Published Language, isolated from producer application/domain.
 * Other contexts can consume the full Environmental Monitoring events directly from the Spring event bus.
 */
@Component
public class TraceabilityEventAdapter {
    private final ApplicationEventPublisher publisher;
    public TraceabilityEventAdapter(ApplicationEventPublisher publisher) { this.publisher = publisher; }
    @EventListener
    public void on(DeviationDetected event) {
        var boundary = event.measuredValue().compareTo(event.minimumValue()) < 0 ? event.minimumValue() : event.maximumValue();
        publisher.publishEvent(new InboundEvents.DeviationDetected(event.organizationId(), event.monitoringZoneId(),
                event.monitoringPointId(), EnvironmentalVariable.valueOf(event.environmentalVariable().name()),
                event.measuredValue().doubleValue(), boundary.doubleValue(), event.recordedAt().toInstant()));
    }
    @EventListener
    public void on(SafeRangeRestored event) {
        publisher.publishEvent(new InboundEvents.SafeRangeRestored(event.organizationId(), event.monitoringZoneId(),
                EnvironmentalVariable.valueOf(event.environmentalVariable().name()), event.restoredAt().toInstant()));
    }
}
