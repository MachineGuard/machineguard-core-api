package com.machineguard.platform.alertincident.domain.repositories;

import com.machineguard.platform.alertincident.domain.model.Alert;
import com.machineguard.platform.alertincident.domain.model.AlertSummary;
import com.machineguard.platform.alertincident.domain.queries.AlertQueries.GetAlerts;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface AlertRepository {
    List<AlertSummary> findAlerts(GetAlerts query);
    Optional<AlertSummary> findSummaryByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<Alert> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<UUID> findIncidentIdById(UUID alertId);

    Alert save(Alert alert);

    Optional<Alert> findById(UUID id);

    Optional<Alert> findByIncidentId(UUID incidentId);
}
