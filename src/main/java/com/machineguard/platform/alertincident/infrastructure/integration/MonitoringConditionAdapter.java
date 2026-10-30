package com.machineguard.platform.alertincident.infrastructure.integration;

import com.machineguard.platform.alertincident.application.ports.EnvironmentalConditionProvider;
import com.machineguard.platform.alertincident.domain.exceptions.AlertIncidentNotFoundException;
import com.machineguard.platform.alertincident.domain.model.EnvironmentalVariable;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.MeasurementRepository;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.MonitoringZoneRepository;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.ThresholdRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Reuses persisted readings and the producer's lock instead of duplicating environmental state. */
@Component
public class MonitoringConditionAdapter implements EnvironmentalConditionProvider {
    private final MonitoringZoneRepository zones;
    private final MeasurementRepository measurements;
    private final ThresholdRepository thresholds;
    public MonitoringConditionAdapter(MonitoringZoneRepository zones, MeasurementRepository measurements,
                                      ThresholdRepository thresholds) {
        this.zones = zones;
        this.measurements = measurements;
        this.thresholds = thresholds;
    }
    public void lockZone(UUID organizationId, UUID monitoringZoneId) {
        zones.lockByIdAndOrganizationId(monitoringZoneId, organizationId)
                .orElseThrow(() -> new AlertIncidentNotFoundException("Monitoring zone not found"));
    }
    public boolean isWithinSafeRange(UUID organizationId, UUID monitoringZoneId, EnvironmentalVariable variable) {
        var producerVariable = com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable
                .valueOf(variable.name());
        var threshold = thresholds.findByMonitoringZoneIdAndEnvironmentalVariable(monitoringZoneId, producerVariable);
        if (threshold.isEmpty()) return false;
        var latest = measurements.findLatest(organizationId, monitoringZoneId).stream()
                .filter(m -> m.getEnvironmentalVariable() == producerVariable).toList();
        return !latest.isEmpty() && latest.stream().allMatch(m -> threshold.get().contains(m.getMeasuredValue()));
    }
}
