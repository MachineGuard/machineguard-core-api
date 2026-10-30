package com.machineguard.platform.alertincident.infrastructure.persistence.repository;

import com.machineguard.platform.alertincident.domain.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface IncidentJpaRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incident i where i.id = :id and i.organizationId = :organizationId")
    Optional<Incident> lockByIdAndOrganizationId(UUID id, UUID organizationId);
    @Query("select i.monitoringZoneId from Incident i where i.id = :id and i.organizationId = :organizationId")
    Optional<UUID> findZoneIdByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Incident> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Incident> findByOriginMeasurementId(UUID originMeasurementId);

    Optional<Incident> findByOpenKey(String openKey);
}
