package com.machineguard.platform.alertincident.application.ports;

import com.machineguard.platform.alertincident.domain.model.EnvironmentalVariable;
import java.util.UUID;

/** Environmental facts supplied by an integration adapter. */
public interface EnvironmentalConditionProvider {
    /** Hold the producer's zone lock until commit; acquire before incident locks. */
    void lockZone(UUID organizationId, UUID monitoringZoneId);
    boolean isWithinSafeRange(UUID organizationId, UUID monitoringZoneId, EnvironmentalVariable variable);
}
