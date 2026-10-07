package com.machineguard.platform.environmentalmonitoring.domain.repositories;

import com.machineguard.platform.environmentalmonitoring.domain.model.MonitoringPoint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonitoringPointRepository {
    MonitoringPoint save(MonitoringPoint point);
    Optional<MonitoringPoint> findByIdAndMonitoringZoneId(UUID id, UUID monitoringZoneId);
    List<MonitoringPoint> findByMonitoringZoneIdOrderByNameAsc(UUID monitoringZoneId);
}
