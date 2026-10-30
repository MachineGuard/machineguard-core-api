package com.machineguard.platform.alertincident.infrastructure.persistence.repository;

import com.machineguard.platform.alertincident.domain.model.CorrectiveAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CorrectiveActionJpaRepository extends JpaRepository<CorrectiveAction, UUID> {

    List<CorrectiveAction> findByIncidentIdOrderByPerformedAtAsc(UUID incidentId);
}
