package com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.adapter;

import com.machineguard.platform.environmentalmonitoring.domain.model.SensorNode;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.SensorNodeRepository;
import com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.repository.SensorNodeJpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

@Repository
public class JpaSensorNodeRepositoryAdapter implements SensorNodeRepository {
    private final SensorNodeJpaRepository repository;

    public JpaSensorNodeRepositoryAdapter(SensorNodeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public SensorNode save(SensorNode node) { return repository.save(node); }
    @Override
    public Optional<SensorNode> findByIdAndMonitoringPointId(UUID id, UUID monitoringPointId) {
        return repository.findByIdAndMonitoringPointId(id, monitoringPointId);
    }
    @Override
    public List<SensorNode> findByMonitoringPointIdIn(Collection<UUID> pointIds) {
        return repository.findByMonitoringPointIdIn(pointIds);
    }
}
