package com.machineguard.platform.environmentalmonitoring.domain.repositories;

import com.machineguard.platform.environmentalmonitoring.domain.model.SensorNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface SensorNodeRepository {
    SensorNode save(SensorNode node);
    boolean existsByDeviceCode(String deviceCode);
    Optional<SensorNode> findByIdAndMonitoringPointId(UUID id, UUID monitoringPointId);
    List<SensorNode> findByMonitoringPointIdIn(Collection<UUID> pointIds);
}
