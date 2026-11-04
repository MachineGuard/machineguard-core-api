package com.machineguard.platform.environmentalmonitoring.application.ports;

import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.DeviationDetected;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.SafeRangeRestored;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.ThresholdConfigured;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.LateDeviationDetected;

/** Publishes producer-owned events without exposing transport or consumer contracts to application services. */
public interface EnvironmentalMonitoringEventPublisher {
    void publish(DeviationDetected event);
    void publish(LateDeviationDetected event);

    void publish(SafeRangeRestored event);

    void publish(ThresholdConfigured event);
}
