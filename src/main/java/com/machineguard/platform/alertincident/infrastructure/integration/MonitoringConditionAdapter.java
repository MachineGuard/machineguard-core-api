package com.machineguard.platform.alertincident.infrastructure.integration;

import com.machineguard.platform.alertincident.application.ports.EnvironmentalConditionProvider;
import com.machineguard.platform.alertincident.domain.exceptions.AlertIncidentNotFoundException;
import com.machineguard.platform.alertincident.domain.model.EnvironmentalVariable;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.MonitoringZoneRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Reuses persisted readings and the producer's lock instead of duplicating environmental state. */
@Component
public class MonitoringConditionAdapter implements EnvironmentalConditionProvider {
    private final MonitoringZoneRepository zones;
    private final com.machineguard.platform.environmentalmonitoring.application.MonitoringEvidenceService evidence;
    public MonitoringConditionAdapter(MonitoringZoneRepository zones, com.machineguard.platform.environmentalmonitoring.application.MonitoringEvidenceService evidence) {
        this.evidence=evidence;
        this.zones = zones;
    }
    public void lockZone(UUID organizationId, UUID monitoringZoneId) {
        zones.lockByIdAndOrganizationId(monitoringZoneId, organizationId)
                .orElseThrow(() -> new AlertIncidentNotFoundException("Monitoring zone not found"));
    }
    public boolean isWithinSafeRange(UUID organizationId, UUID monitoringZoneId, EnvironmentalVariable variable) {
        return resolutionBlockReasons(organizationId,monitoringZoneId,variable).isEmpty();
    }
    @Override public java.util.List<String> resolutionBlockReasons(UUID organizationId,UUID monitoringZoneId,EnvironmentalVariable variable) {
        return evidence.assess(organizationId,monitoringZoneId,variable==null?null:
                com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable.valueOf(variable.name())).reasons();
    }
}
