package com.machineguard.platform.alertincident.infrastructure.persistence.repository;

import com.machineguard.platform.alertincident.domain.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AlertJpaRepository extends JpaRepository<Alert, UUID> {

    Optional<Alert> findByIncidentId(UUID incidentId);
}
