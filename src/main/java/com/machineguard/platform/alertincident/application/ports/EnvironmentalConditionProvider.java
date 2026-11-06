package com.machineguard.platform.alertincident.application.ports;

import com.machineguard.platform.alertincident.domain.model.EnvironmentalVariable;
import java.util.UUID;
import java.util.List;

/** Environmental facts supplied by an integration adapter. */
public interface EnvironmentalConditionProvider {
    /** Hold the producer's zone lock until commit; acquire before incident locks. */
    void lockZone(UUID organizationId, UUID monitoringZoneId);
    default List<String> resolutionBlockReasons(UUID organizationId,UUID monitoringZoneId,EnvironmentalVariable variable) {
        return isWithinSafeRange(organizationId,monitoringZoneId,variable)?List.of():List.of("OUT_OF_RANGE");
    }
    boolean isWithinSafeRange(UUID organizationId, UUID monitoringZoneId, EnvironmentalVariable variable);
}
