package com.machineguard.platform.environmentalmonitoring.domain.repositories;

import com.machineguard.platform.environmentalmonitoring.domain.model.Threshold;
import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThresholdRepository {
    Threshold save(Threshold threshold);
    Optional<Threshold> findByMonitoringZoneIdAndEnvironmentalVariable(UUID monitoringZoneId, EnvironmentalVariable environmentalVariable);
    List<Threshold> findByMonitoringZoneId(UUID monitoringZoneId);
}
