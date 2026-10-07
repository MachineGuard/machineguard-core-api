package com.machineguard.platform.environmentalmonitoring.application.commandservices;

import com.machineguard.platform.environmentalmonitoring.application.ports.EnvironmentalMonitoringEventPublisher;
import com.machineguard.platform.environmentalmonitoring.domain.commands.ConfigureThresholdCommand;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.ThresholdConfigured;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.EnvironmentalMonitoringNotFoundException;
import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class ConfigureThresholdCommandService {
    private final MonitoringZoneRepository zones;
    private final ThresholdRepository thresholds;
    private final EnvironmentalMonitoringEventPublisher events;
    private final Clock clock;

    public ConfigureThresholdCommandService(MonitoringZoneRepository zones, ThresholdRepository thresholds,
            EnvironmentalMonitoringEventPublisher events, Clock clock) {
        this.zones = zones;
        this.thresholds = thresholds;
        this.events = events;
        this.clock = clock;
    }

    /** Creates the Safe Range of the zone for the variable, or replaces it when one already exists. */
    @Transactional
    public Threshold handle(ConfigureThresholdCommand command) {
        // Same lock as ingestion: a reading is never evaluated against a half-updated Safe Range.
        MonitoringZone zone = zones.lockByIdAndOrganizationId(command.monitoringZoneId(), command.organizationId())
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Monitoring zone not found"));
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Threshold threshold = thresholds.findByMonitoringZoneIdAndEnvironmentalVariable(zone.getId(), command.environmentalVariable())
                .map(existing -> existing.reconfigure(command.minimumValue(), command.maximumValue(), now))
                .orElseGet(() -> Threshold.configure(zone.getId(), command.environmentalVariable(),
                        command.minimumValue(), command.maximumValue(), now));
        threshold = thresholds.save(threshold);
        events.publish(new ThresholdConfigured(threshold.getId(), command.organizationId(), zone.getId(),
                threshold.getEnvironmentalVariable(), threshold.getMinimumValue(), threshold.getMaximumValue(), now));
        return threshold;
    }
}
