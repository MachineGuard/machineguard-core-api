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

public interface ExcursionRepository extends JpaRepository<Excursion, Long>, JpaSpecificationExecutor<Excursion> {

    Optional<Excursion> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<Excursion> findByOrganizationIdAndMonitoringZoneIdAndEnvironmentalVariableAndStatus(
            Long organizationId, Long monitoringZoneId, EnvironmentalVariable variable, ExcursionStatus status);

    List<Excursion> findByOrganizationIdAndMonitoringZoneIdAndStatus(
            Long organizationId, Long monitoringZoneId, ExcursionStatus status);

    List<Excursion> findByOrganizationIdAndStatusOrderByStartedAtDesc(Long organizationId, ExcursionStatus status);

    Optional<Excursion> findFirstByOrganizationIdAndIncidentId(Long organizationId, Long incidentId);

    List<Excursion> findByOrganizationIdAndMonitoringZoneIdAndStatusAndIncidentIdIsNullOrderByEndedAtDesc(
            Long organizationId, Long monitoringZoneId, ExcursionStatus status);

    /** Excursiones cerradas de una zona que se solapan con el periodo [from, to]. */
    @Query("""
            select e from Excursion e
            where e.organizationId = :organizationId and e.monitoringZoneId = :zoneId
              and e.status = com.machineguard.platform.traceability.domain.model.ExcursionStatus.CLOSED
              and e.startedAt <= :to and e.endedAt >= :from
            order by e.startedAt
            """)
    List<Excursion> findClosedOverlapping(@Param("organizationId") Long organizationId, @Param("zoneId") Long zoneId,
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
