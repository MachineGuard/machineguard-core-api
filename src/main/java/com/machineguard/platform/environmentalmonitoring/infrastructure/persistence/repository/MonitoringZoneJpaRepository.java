package com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.repository;

import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface MonitoringZoneJpaRepository extends JpaRepository<MonitoringZone, UUID> {
    Optional<MonitoringZone> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<MonitoringZone> findByOrganizationIdOrderByNameAsc(UUID organizationId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select z from MonitoringZone z where z.id = :id and z.organizationId = :organizationId")
    Optional<MonitoringZone> lockByIdAndOrganizationId(UUID id, UUID organizationId);
}
