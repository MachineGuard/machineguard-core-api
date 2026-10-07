package com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.repository;

import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface ThresholdJpaRepository extends JpaRepository<Threshold, UUID> {
    Optional<Threshold> findByMonitoringZoneIdAndEnvironmentalVariable(UUID monitoringZoneId, EnvironmentalVariable environmentalVariable);
    List<Threshold> findByMonitoringZoneId(UUID monitoringZoneId);
}
