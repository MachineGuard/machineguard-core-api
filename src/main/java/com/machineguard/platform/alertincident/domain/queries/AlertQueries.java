package com.machineguard.platform.alertincident.domain.queries;

import com.machineguard.platform.alertincident.domain.model.AlertStatus;
import java.util.Objects;
import java.util.UUID;

public final class AlertQueries {
    private AlertQueries() {}

    public record GetAlerts(UUID organizationId, UUID monitoringZoneId, AlertStatus status) {
        public GetAlerts { Objects.requireNonNull(organizationId); }
    }

    public record GetAlertById(UUID organizationId, UUID alertId) {
        public GetAlertById {
            Objects.requireNonNull(organizationId);
            Objects.requireNonNull(alertId);
        }
    }
}
