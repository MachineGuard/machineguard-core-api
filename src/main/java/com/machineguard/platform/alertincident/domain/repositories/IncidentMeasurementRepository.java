package com.machineguard.platform.alertincident.domain.repositories;
import java.util.Optional;
import java.util.UUID;

public interface IncidentMeasurementRepository {
    Optional<UUID> findIncidentId(UUID measurementId);
    void record(UUID measurementId, UUID incidentId);
}
