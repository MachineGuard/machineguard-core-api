package com.machineguard.platform.traceability.application.ports;

import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;

import java.time.Instant;
import java.util.List;

/**
 * Puerto hacia el Measurement History conservado por Environmental Monitoring
 * (Published Language / Conformist: se comparte su Ubiquitous Language).
 */
public interface MeasurementHistoryProvider {

    List<MeasurementPoint> findByZone(Long organizationId, Long monitoringZoneId, EnvironmentalVariable variable,
                                      Instant from, Instant to);

    record MeasurementPoint(Long monitoringPointId, EnvironmentalVariable environmentalVariable, double value,
                            Instant recordedAt) {
    }
}
