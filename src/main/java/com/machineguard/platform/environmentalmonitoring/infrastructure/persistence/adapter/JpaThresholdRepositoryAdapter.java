package com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.adapter;

import com.machineguard.platform.environmentalmonitoring.domain.model.Threshold;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.ThresholdRepository;
import com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.repository.ThresholdJpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;

@Repository
public class JpaThresholdRepositoryAdapter implements ThresholdRepository {
    private final ThresholdJpaRepository repository;

    public JpaThresholdRepositoryAdapter(ThresholdJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Threshold save(Threshold threshold) { return repository.save(threshold); }
    @Override
    public Optional<Threshold> findByMonitoringZoneIdAndEnvironmentalVariable(UUID monitoringZoneId, EnvironmentalVariable environmentalVariable) {
        return repository.findByMonitoringZoneIdAndEnvironmentalVariable(monitoringZoneId, environmentalVariable);
    }
    @Override
    public List<Threshold> findByMonitoringZoneId(UUID monitoringZoneId) {
        return repository.findByMonitoringZoneId(monitoringZoneId);
    }
}
