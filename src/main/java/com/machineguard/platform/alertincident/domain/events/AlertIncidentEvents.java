package com.machineguard.platform.alertincident.domain.events;

import com.machineguard.platform.alertincident.domain.model.AlertSeverity;
import com.machineguard.platform.alertincident.domain.model.EnvironmentalVariable;

import java.time.Instant;
import java.util.UUID;

/** Eventos de dominio publicados por Alert and Incident Management. */
public final class AlertIncidentEvents {

    private AlertIncidentEvents() {
    }

    public record AlertRaised(UUID alertId, UUID incidentId, UUID organizationId, UUID monitoringZoneId,
                              AlertSeverity severity, Instant raisedAt) {
    }

    public record AlertAcknowledged(UUID alertId, UUID incidentId, UUID organizationId, UUID acknowledgedBy,
                                    Instant acknowledgedAt) {
    }

    public record CorrectiveActionRegistered(UUID correctiveActionId, UUID incidentId, UUID organizationId,
                                             UUID performedBy, Instant performedAt) {
    }

    /** Lo consume Traceability and Quality para vincular la accion correctiva con la excursion. */
    public record IncidentResolved(UUID incidentId, UUID originMeasurementId, UUID organizationId, UUID monitoringZoneId,
                                   EnvironmentalVariable environmentalVariable, String correctiveAction,
                                   Instant resolvedAt, Instant openedAt) {
    }
}
