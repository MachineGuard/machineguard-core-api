package com.machineguard.platform.alertincident.domain.queries;

import com.machineguard.platform.alertincident.domain.model.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public final class AlertIncidentQueries {

    private AlertIncidentQueries() {
    }

    /** Todos los filtros salvo organizationId son opcionales; el periodo filtra por fecha de apertura. */
    public record GetIncidentHistoryQuery(UUID organizationId, UUID monitoringZoneId, IncidentStatus status,
                                          Instant from, Instant to, int limit) {
    }

    public record GetIncidentByIdQuery(UUID organizationId, UUID incidentId) {
    }
}
