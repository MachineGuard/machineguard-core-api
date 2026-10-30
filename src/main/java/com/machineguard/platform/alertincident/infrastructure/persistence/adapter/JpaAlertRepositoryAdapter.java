package com.machineguard.platform.alertincident.infrastructure.persistence.adapter;

import com.machineguard.platform.alertincident.domain.model.Alert;
import com.machineguard.platform.alertincident.domain.repositories.AlertRepository;
import com.machineguard.platform.alertincident.infrastructure.persistence.repository.AlertJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAlertRepositoryAdapter implements AlertRepository {

    private final AlertJpaRepository repository;

    public JpaAlertRepositoryAdapter(AlertJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Alert save(Alert alert) {
        return repository.save(alert);
    }

    @Override
    public Optional<Alert> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Alert> findByIncidentId(UUID incidentId) {
        return repository.findByIncidentId(incidentId);
    }
}
