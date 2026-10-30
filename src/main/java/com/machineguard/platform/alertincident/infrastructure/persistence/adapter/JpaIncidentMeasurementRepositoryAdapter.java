package com.machineguard.platform.alertincident.infrastructure.persistence.adapter;

import com.machineguard.platform.alertincident.domain.model.IncidentMeasurement;
import com.machineguard.platform.alertincident.domain.repositories.IncidentMeasurementRepository;
import com.machineguard.platform.alertincident.infrastructure.persistence.repository.IncidentMeasurementJpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaIncidentMeasurementRepositoryAdapter implements IncidentMeasurementRepository {
    private final IncidentMeasurementJpaRepository repository;
    public JpaIncidentMeasurementRepositoryAdapter(IncidentMeasurementJpaRepository repository) { this.repository = repository; }
    public Optional<UUID> findIncidentId(UUID measurementId) {
        return repository.findById(measurementId).map(IncidentMeasurement::getIncidentId);
    }
    public void record(UUID measurementId, UUID incidentId) {
        repository.saveAndFlush(new IncidentMeasurement(measurementId, incidentId));
    }
}
