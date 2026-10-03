package com.machineguard.platform.traceability.application.ports;

import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Puerto hacia el Measurement History conservado por Environmental Monitoring
 * (Published Language / Conformist: se comparte su Ubiquitous Language).
 */
public interface MeasurementHistoryProvider {

    List<MeasurementPoint> findByZone(UUID organizationId, UUID monitoringZoneId, EnvironmentalVariable variable,
                                      Instant from, Instant to);

    record MeasurementPoint(UUID monitoringPointId, EnvironmentalVariable environmentalVariable, double value,
                            Instant recordedAt) {
    }
}
