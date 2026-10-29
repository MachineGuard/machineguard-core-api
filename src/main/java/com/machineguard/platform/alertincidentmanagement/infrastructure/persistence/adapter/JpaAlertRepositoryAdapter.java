package com.machineguard.platform.alertincidentmanagement.infrastructure.persistence.adapter;

import com.machineguard.platform.alertincidentmanagement.domain.model.Alert;
import com.machineguard.platform.alertincidentmanagement.domain.queries.AlertQueries.GetAlerts;
import com.machineguard.platform.alertincidentmanagement.domain.repositories.AlertRepository;
import com.machineguard.platform.alertincidentmanagement.infrastructure.persistence.repository.AlertJpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAlertRepositoryAdapter implements AlertRepository {
    private final AlertJpaRepository repository;

    public JpaAlertRepositoryAdapter(AlertJpaRepository repository) { this.repository = repository; }

    @Override
    public Alert save(Alert alert) { return repository.saveAndFlush(alert); }

    @Override
    public List<Alert> findAlerts(GetAlerts query) {
        return repository.findAlerts(query.organizationId(), query.monitoringZoneId(),
                query.status() == null ? null : query.status().name());
    }

    @Override
    public Optional<Alert> findByIdAndOrganizationId(UUID alertId, UUID organizationId) {
        return repository.findByIdAndOrganizationId(Objects.requireNonNull(alertId), Objects.requireNonNull(organizationId));
    }
}
