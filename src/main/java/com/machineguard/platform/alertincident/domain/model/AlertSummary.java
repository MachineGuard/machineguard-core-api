package com.machineguard.platform.alertincident.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Tenant-scoped read model; the zone belongs to the incident rather than the alert table. */
public record AlertSummary(UUID id, UUID incidentId, UUID monitoringZoneId, AlertSeverity severity,
                           AlertStatus status, String message, Instant raisedAt,
                           Instant acknowledgedAt, Instant escalatedAt,boolean historical) { }
