package com.machineguard.platform.alertincident.infrastructure.persistence.adapter;

import com.machineguard.platform.alertincident.domain.model.Alert;
import com.machineguard.platform.alertincident.domain.model.AlertSummary;
import com.machineguard.platform.alertincident.domain.queries.AlertQueries.GetAlerts;
import com.machineguard.platform.alertincident.domain.repositories.AlertRepository;
import com.machineguard.platform.alertincident.infrastructure.persistence.repository.AlertJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class JpaAlertRepositoryAdapter implements AlertRepository {

    private final AlertJpaRepository repository;

    public JpaAlertRepositoryAdapter(AlertJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Alert save(Alert alert) {
        return repository.saveAndFlush(alert);
    }

    @Override
    public List<AlertSummary> findAlerts(GetAlerts query) {
        return repository.findAlerts(query.organizationId(), query.monitoringZoneId(), query.status());
    }

    @Override
    public Optional<AlertSummary> findSummaryByIdAndOrganizationId(UUID id, UUID organizationId) {
        return repository.findSummaryByIdAndOrganizationId(id, Objects.requireNonNull(organizationId));
    }

    @Override
    public Optional<Alert> findByIdAndOrganizationId(UUID id, UUID organizationId) {
        return repository.findByIdAndOrganizationId(id, Objects.requireNonNull(organizationId));
    }

    @Override
    public Optional<UUID> findIncidentIdById(UUID id) {
        return repository.findIncidentIdById(id);
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
