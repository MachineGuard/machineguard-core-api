package com.machineguard.platform.alertincident.domain.repositories;

import com.machineguard.platform.alertincident.domain.model.Incident;
import com.machineguard.platform.alertincident.domain.model.IncidentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository {
    Optional<Incident> lockByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<UUID> findZoneIdByIdAndOrganizationId(UUID id, UUID organizationId);

    Incident save(Incident incident);

    Optional<Incident> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Incident> findByOriginMeasurementId(UUID originMeasurementId);

    /** Incidente sin resolver para la clave organizacion:zona:variable (a lo sumo uno). */
    Optional<Incident> findByOpenKey(String openKey);

    List<Incident> search(UUID organizationId, UUID monitoringZoneId, IncidentStatus status, Instant from,
                          Instant to, int limit);
}
