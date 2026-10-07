package com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.adapter;

import com.machineguard.platform.environmentalmonitoring.domain.model.MonitoringPoint;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.MonitoringPointRepository;
import com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.repository.MonitoringPointJpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaMonitoringPointRepositoryAdapter implements MonitoringPointRepository {
    private final MonitoringPointJpaRepository repository;

    public JpaMonitoringPointRepositoryAdapter(MonitoringPointJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public MonitoringPoint save(MonitoringPoint point) { return repository.save(point); }
    @Override
    public Optional<MonitoringPoint> findByIdAndMonitoringZoneId(UUID id, UUID monitoringZoneId) {
        return repository.findByIdAndMonitoringZoneId(id, monitoringZoneId);
    }
    @Override
    public List<MonitoringPoint> findByMonitoringZoneIdOrderByNameAsc(UUID monitoringZoneId) {
        return repository.findByMonitoringZoneIdOrderByNameAsc(monitoringZoneId);
    }
}
