package com.machineguard.platform.alertincidentmanagement.infrastructure.persistence.repository;

import com.machineguard.platform.alertincidentmanagement.domain.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertJpaRepository extends JpaRepository<Alert, UUID> {
    // Organization and zone belong to incidents. Filter in SQL, without duplicating its JPA entity.
    // Explicit casts also type nullable parameters correctly on PostgreSQL.
    @Query(value = """
            SELECT a.* FROM alerts a JOIN incidents i ON i.id = a.incident_id
            WHERE i.organization_id = :organizationId
              AND (CAST(:monitoringZoneId AS UUID) IS NULL OR i.monitoring_zone_id = CAST(:monitoringZoneId AS UUID))
              AND (CAST(:status AS VARCHAR) IS NULL OR a.status = CAST(:status AS VARCHAR))
            ORDER BY a.raised_at DESC, a.id
            """, nativeQuery = true)
    List<Alert> findAlerts(UUID organizationId, UUID monitoringZoneId, String status);

    @Query(value = """
            SELECT a.* FROM alerts a JOIN incidents i ON i.id = a.incident_id
            WHERE a.id = :alertId AND i.organization_id = :organizationId
            """, nativeQuery = true)
    Optional<Alert> findByIdAndOrganizationId(UUID alertId, UUID organizationId);
}
