package com.machineguard.platform.environmentalmonitoring.domain.repositories;

import com.machineguard.platform.environmentalmonitoring.domain.model.MonitoringZone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonitoringZoneRepository {
    MonitoringZone save(MonitoringZone zone);
    Optional<MonitoringZone> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<MonitoringZone> findByOrganizationIdOrderByNameAsc(UUID organizationId);
    /** Acquire exclusive zone access for the surrounding ingestion transaction. */
    Optional<MonitoringZone> lockByIdAndOrganizationId(UUID id, UUID organizationId);
}
