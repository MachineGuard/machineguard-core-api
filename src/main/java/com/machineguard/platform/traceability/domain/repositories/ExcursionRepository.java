package com.machineguard.platform.traceability.domain.repositories;

import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;
import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.model.ExcursionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExcursionRepository extends JpaRepository<Excursion, UUID>, JpaSpecificationExecutor<Excursion> {

    Optional<Excursion> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Excursion> findByOrganizationIdAndMonitoringZoneIdAndEnvironmentalVariableAndStatus(
            UUID organizationId, UUID monitoringZoneId, EnvironmentalVariable variable, ExcursionStatus status);

    List<Excursion> findByOrganizationIdAndMonitoringZoneIdAndStatus(
            UUID organizationId, UUID monitoringZoneId, ExcursionStatus status);

    List<Excursion> findByOrganizationIdAndStatusOrderByStartedAtDesc(UUID organizationId, ExcursionStatus status);

    Optional<Excursion> findFirstByOrganizationIdAndIncidentId(UUID organizationId, UUID incidentId);

    List<Excursion> findByOrganizationIdAndMonitoringZoneIdAndStatusAndIncidentIdIsNullOrderByEndedAtDesc(
            UUID organizationId, UUID monitoringZoneId, ExcursionStatus status);

    /** Excursiones cerradas de una zona que se solapan con el periodo [from, to]. */
    @Query("""
            select e from Excursion e
            where e.organizationId = :organizationId and e.monitoringZoneId = :zoneId
              and e.status = com.machineguard.platform.traceability.domain.model.ExcursionStatus.CLOSED
              and e.startedAt <= :to and e.endedAt >= :from
            order by e.startedAt
            """)
    List<Excursion> findClosedOverlapping(@Param("organizationId") UUID organizationId, @Param("zoneId") UUID zoneId,
                                          @Param("from") Instant from, @Param("to") Instant to);

    /** Excursiones cerradas antes del corte que no forman parte de ningun reporte sellado. */
    @Query("""
            select e from Excursion e
            where e.status = com.machineguard.platform.traceability.domain.model.ExcursionStatus.CLOSED
              and e.endedAt < :cutoff
              and not exists (select r.id from TraceabilityReport r join r.excursionIds x where x = e.id)
            """)
    List<Excursion> findPurgeable(@Param("cutoff") Instant cutoff);
}
