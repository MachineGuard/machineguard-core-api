package com.machineguard.platform.alertincident.infrastructure.persistence.repository;

import com.machineguard.platform.alertincident.domain.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface IncidentJpaRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    Optional<Incident> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Incident> findByDeviationId(UUID deviationId);

    Optional<Incident> findByOpenKey(String openKey);
}
