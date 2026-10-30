package com.machineguard.platform.alertincident.infrastructure.persistence.adapter;

import com.machineguard.platform.alertincident.domain.model.Incident;
import com.machineguard.platform.alertincident.domain.model.IncidentStatus;
import com.machineguard.platform.alertincident.domain.repositories.IncidentRepository;
import com.machineguard.platform.alertincident.infrastructure.persistence.repository.IncidentJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaIncidentRepositoryAdapter implements IncidentRepository {

    private final IncidentJpaRepository repository;

    public JpaIncidentRepositoryAdapter(IncidentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Incident save(Incident incident) {
        // saveAndFlush: una violacion de la clave unica de incidentes abiertos debe aflorar aqui y no al commit.
        return repository.saveAndFlush(incident);
    }

    @Override
    public Optional<Incident> lockByIdAndOrganizationId(UUID id, UUID organizationId) {
        return repository.lockByIdAndOrganizationId(id, organizationId);
    }

    @Override
    public Optional<UUID> findZoneIdByIdAndOrganizationId(UUID id, UUID organizationId) {
        return repository.findZoneIdByIdAndOrganizationId(id, organizationId);
    }

    @Override
    public Optional<Incident> findByIdAndOrganizationId(UUID id, UUID organizationId) {
        return repository.findByIdAndOrganizationId(id, organizationId);
    }

    @Override
    public Optional<Incident> findByOriginMeasurementId(UUID originMeasurementId) {
        return repository.findByOriginMeasurementId(originMeasurementId);
    }

    @Override
    public Optional<Incident> findByOpenKey(String openKey) {
        return repository.findByOpenKey(openKey);
    }

    @Override
    public List<Incident> search(UUID organizationId, UUID monitoringZoneId, IncidentStatus status, Instant from,
                                 Instant to, int limit) {
        Specification<Incident> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));
            if (monitoringZoneId != null) {
                predicates.add(cb.equal(root.get("monitoringZoneId"), monitoringZoneId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("openedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("openedAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "openedAt"))).getContent();
    }
}
