package com.machineguard.platform.iam.domain.port;

import com.machineguard.platform.iam.domain.model.Organization;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {
    Optional<Organization> findById(UUID id);
    Optional<Organization> findByIdForUpdate(UUID id);
    Optional<Organization> findByAcquisitionRequestId(UUID requestId);
    Organization save(Organization organization);
    boolean existsAny();
}
