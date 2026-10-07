package com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.repository;

import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface MonitoringPointJpaRepository extends JpaRepository<MonitoringPoint, UUID> {
    Optional<MonitoringPoint> findByIdAndMonitoringZoneId(UUID id, UUID monitoringZoneId);
    List<MonitoringPoint> findByMonitoringZoneIdOrderByNameAsc(UUID monitoringZoneId);
}
