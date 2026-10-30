package com.machineguard.platform.alertincident.infrastructure.persistence.repository;

import com.machineguard.platform.alertincident.domain.model.Alert;
import com.machineguard.platform.alertincident.domain.model.AlertSummary;
import com.machineguard.platform.alertincident.domain.model.AlertStatus;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AlertJpaRepository extends JpaRepository<Alert, UUID> {
    @Query("""
            select new com.machineguard.platform.alertincident.domain.model.AlertSummary(
                a.id, a.incidentId, i.monitoringZoneId, a.severity, a.status, a.message,
                a.raisedAt, a.acknowledgedAt, a.escalatedAt)
            from Alert a join Incident i on i.id = a.incidentId
            where i.organizationId = :organizationId
              and (:monitoringZoneId is null or i.monitoringZoneId = :monitoringZoneId)
              and (:status is null or a.status = :status)
            order by a.raisedAt desc, a.id
            """)
    List<AlertSummary> findAlerts(UUID organizationId, UUID monitoringZoneId, AlertStatus status);

    @Query("""
            select new com.machineguard.platform.alertincident.domain.model.AlertSummary(
                a.id, a.incidentId, i.monitoringZoneId, a.severity, a.status, a.message,
                a.raisedAt, a.acknowledgedAt, a.escalatedAt)
            from Alert a join Incident i on i.id = a.incidentId
            where a.id = :id and i.organizationId = :organizationId
            """)
    Optional<AlertSummary> findSummaryByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("""
            select a from Alert a join Incident i on i.id = a.incidentId
            where a.id = :id and i.organizationId = :organizationId
            """)
    Optional<Alert> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("select a.incidentId from Alert a where a.id = :id")
    Optional<UUID> findIncidentIdById(UUID id);

    Optional<Alert> findByIncidentId(UUID incidentId);
}
