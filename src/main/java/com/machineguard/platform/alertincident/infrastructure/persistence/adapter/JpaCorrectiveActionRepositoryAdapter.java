package com.machineguard.platform.alertincident.infrastructure.persistence.adapter;

import com.machineguard.platform.alertincident.domain.model.CorrectiveAction;
import com.machineguard.platform.alertincident.domain.repositories.CorrectiveActionRepository;
import com.machineguard.platform.alertincident.infrastructure.persistence.repository.CorrectiveActionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JpaCorrectiveActionRepositoryAdapter implements CorrectiveActionRepository {

    private final CorrectiveActionJpaRepository repository;

    public JpaCorrectiveActionRepositoryAdapter(CorrectiveActionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public CorrectiveAction save(CorrectiveAction action) {
        return repository.save(action);
    }

    @Override
    public List<CorrectiveAction> findByIncidentId(UUID incidentId) {
        return repository.findByIncidentIdOrderByPerformedAtAsc(incidentId);
    }
}
