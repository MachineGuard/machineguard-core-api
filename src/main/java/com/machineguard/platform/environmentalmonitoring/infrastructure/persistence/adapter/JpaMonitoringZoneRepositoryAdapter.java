package com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.adapter;

import com.machineguard.platform.environmentalmonitoring.domain.model.MonitoringZone;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.MonitoringZoneRepository;
import com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.repository.MonitoringZoneJpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaMonitoringZoneRepositoryAdapter implements MonitoringZoneRepository {
    private final MonitoringZoneJpaRepository repository;

    public JpaMonitoringZoneRepositoryAdapter(MonitoringZoneJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public MonitoringZone save(MonitoringZone zone) { return repository.save(zone); }
    @Override
    public Optional<MonitoringZone> findByIdAndOrganizationId(UUID id, UUID organizationId) {
        return repository.findByIdAndOrganizationId(id, organizationId);
    }
    @Override
    public List<MonitoringZone> findByOrganizationIdOrderByNameAsc(UUID organizationId) {
        return repository.findByOrganizationIdOrderByNameAsc(organizationId);
    }
    @Override
    public Optional<MonitoringZone> lockByIdAndOrganizationId(UUID id, UUID organizationId) {
        return repository.lockByIdAndOrganizationId(id, organizationId);
    }
}
