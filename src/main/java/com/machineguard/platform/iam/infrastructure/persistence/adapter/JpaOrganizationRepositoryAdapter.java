package com.machineguard.platform.iam.infrastructure.persistence.adapter;

import com.machineguard.platform.iam.domain.model.Organization;
import com.machineguard.platform.iam.domain.port.OrganizationRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.OrganizationJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaOrganizationRepositoryAdapter implements OrganizationRepository {
    private final OrganizationJpaRepository repository;

    public JpaOrganizationRepositoryAdapter(OrganizationJpaRepository repository) { this.repository = repository; }

    @Override public Optional<Organization> findById(UUID id) {
        return repository.findById(id).map(IamEntityMapper::toDomain);
    }

    @Override public Optional<Organization> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(IamEntityMapper::toDomain);
    }

    @Override public Optional<Organization> findByAcquisitionRequestId(UUID requestId) {
        return repository.findByAcquisitionRequestId(requestId).map(IamEntityMapper::toDomain);
    }

    @Override public Organization save(Organization organization) {
        return IamEntityMapper.toDomain(repository.save(IamEntityMapper.toEntity(organization)));
    }

    @Override public boolean existsAny() { return repository.count() > 0; }
}
