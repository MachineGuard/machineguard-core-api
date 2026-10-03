package com.machineguard.platform.traceability.domain.repositories;

import com.machineguard.platform.traceability.domain.model.TraceabilityReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TraceabilityReportRepository extends JpaRepository<TraceabilityReport, UUID> {

    Optional<TraceabilityReport> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<TraceabilityReport> findByOrganizationIdOrderByGeneratedAtDesc(UUID organizationId);

    List<TraceabilityReport> findByOrganizationIdAndMonitoringZoneIdOrderByGeneratedAtDesc(
            UUID organizationId, UUID monitoringZoneId);
}
