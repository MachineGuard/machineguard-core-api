package com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.repository;

import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface SensorNodeJpaRepository extends JpaRepository<SensorNode, UUID> {
    Optional<SensorNode> findByIdAndMonitoringPointId(UUID id, UUID monitoringPointId);
    List<SensorNode> findByMonitoringPointIdIn(Collection<UUID> pointIds);
}
