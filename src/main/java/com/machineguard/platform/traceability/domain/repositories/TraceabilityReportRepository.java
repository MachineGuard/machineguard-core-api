package com.machineguard.platform.traceability.domain.repositories;

import com.machineguard.platform.traceability.domain.model.TraceabilityReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TraceabilityReportRepository extends JpaRepository<TraceabilityReport, Long> {

    Optional<TraceabilityReport> findByIdAndOrganizationId(Long id, Long organizationId);

    List<TraceabilityReport> findByOrganizationIdOrderByGeneratedAtDesc(Long organizationId);

    List<TraceabilityReport> findByOrganizationIdAndMonitoringZoneIdOrderByGeneratedAtDesc(
            Long organizationId, Long monitoringZoneId);
}
