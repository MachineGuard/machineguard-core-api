package com.machineguard.platform.traceability.application.integration;

import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;

import java.time.Instant;

/**
 * Eventos que Traceability & Quality consume de otros Bounded Contexts. Los publica quien los origina con
 * ApplicationEventPublisher dentro de la API central; el contrato es el de estos records (Published Language).
 */
public final class InboundEvents {

    private InboundEvents() {
    }

    /** Environmental Monitoring: una medicion supero un Threshold. */
    public record DeviationDetected(Long organizationId, Long monitoringZoneId, Long monitoringPointId,
                                    EnvironmentalVariable environmentalVariable, double measuredValue,
                                    double thresholdValue, Instant detectedAt) {
    }

    /** Environmental Monitoring: las mediciones de la zona volvieron a su Safe Range. */
    public record SafeRangeRestored(Long organizationId, Long monitoringZoneId,
                                    EnvironmentalVariable environmentalVariable, Instant restoredAt) {
    }

    /**
     * Alert and Incident Management: el incidente se resolvio y se registro la accion correctiva.
     * environmentalVariable puede ser null si el incidente cubre toda la zona.
     */
    public record IncidentResolved(Long organizationId, Long incidentId, Long monitoringZoneId,
                                   EnvironmentalVariable environmentalVariable, String correctiveAction,
                                   Instant resolvedAt) {
    }
}
