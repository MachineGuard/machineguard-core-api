package com.machineguard.platform.traceability.domain.repositories;

import com.machineguard.platform.traceability.domain.model.LateDeviation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LateDeviationRepository extends JpaRepository<LateDeviation,UUID> {
    List<LateDeviation> findByOrganizationIdAndMonitoringZoneIdOrderByRecordedAtDescMeasurementIdAsc(
            UUID organizationId,UUID monitoringZoneId,Pageable pageable);
}
