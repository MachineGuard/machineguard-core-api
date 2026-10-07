package com.machineguard.platform.environmentalmonitoring.infrastructure.integration;

import com.machineguard.platform.environmentalmonitoring.application.ports.EnvironmentalMonitoringEventPublisher;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.DeviationDetected;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.SafeRangeRestored;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Uses the existing synchronous Spring event bus and participates in the caller's transaction. */
@Component
public class SpringEnvironmentalMonitoringEventPublisher implements EnvironmentalMonitoringEventPublisher {
    private final ApplicationEventPublisher publisher;

    public SpringEnvironmentalMonitoringEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DeviationDetected event) {
        publisher.publishEvent(event);
    }

    @Override
    public void publish(SafeRangeRestored event) {
        publisher.publishEvent(event);
    }
}
