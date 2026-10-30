package com.machineguard.platform.alertincidentmanagement.interfaces.rest.resources;

import com.machineguard.platform.alertincidentmanagement.domain.model.*;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class AlertResources {
    private AlertResources() {}

    public record AlertResource(UUID id, UUID incidentId, AlertSeverity severity, String message,
            AlertStatus status, OffsetDateTime raisedAt, OffsetDateTime acknowledgedAt, OffsetDateTime escalatedAt) {
        public static AlertResource from(Alert alert) {
            return new AlertResource(alert.getId(), alert.getIncidentId(), alert.getSeverity(), alert.getMessage(),
                    alert.getStatus(), alert.getRaisedAt(), alert.getAcknowledgedAt(), alert.getEscalatedAt());
        }
    }
}
