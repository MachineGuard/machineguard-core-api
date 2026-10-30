package com.machineguard.platform.alertincident.domain.commands;

import com.machineguard.platform.alertincident.domain.model.EnvironmentalVariable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AlertIncidentCommands {

    private AlertIncidentCommands() {
    }

    /** Origen: DeviationDetected de Environmental Monitoring. */
    public record RaiseAlertCommand(UUID measurementId, UUID organizationId, UUID monitoringZoneId,
                                    EnvironmentalVariable environmentalVariable, BigDecimal measuredValue,
                                    BigDecimal minimumValue, BigDecimal maximumValue, Instant detectedAt) {
    }

    public record AcknowledgeAlertCommand(UUID organizationId, UUID alertId, UUID acknowledgedBy, String note) {
    }

    public record RegisterCorrectiveActionCommand(UUID organizationId, UUID incidentId, UUID performedBy,
                                                  String description, String notes) {
    }

    public record ResolveIncidentCommand(UUID organizationId, UUID incidentId, UUID resolvedBy, String notes) {
    }
}
