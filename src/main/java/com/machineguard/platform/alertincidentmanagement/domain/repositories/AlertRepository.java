package com.machineguard.platform.alertincidentmanagement.domain.repositories;

import com.machineguard.platform.alertincidentmanagement.domain.model.Alert;
import com.machineguard.platform.alertincidentmanagement.domain.queries.AlertQueries.GetAlerts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository {
    Alert save(Alert alert);
    List<Alert> findAlerts(GetAlerts query);
    Optional<Alert> findByIdAndOrganizationId(UUID alertId, UUID organizationId);
}
