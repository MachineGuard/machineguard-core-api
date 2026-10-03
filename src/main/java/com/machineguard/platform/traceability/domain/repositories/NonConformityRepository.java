package com.machineguard.platform.traceability.domain.repositories;

import com.machineguard.platform.traceability.domain.model.NonConformity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NonConformityRepository extends JpaRepository<NonConformity, UUID> {

    Optional<NonConformity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<NonConformity> findByExcursionIdAndOrganizationIdOrderByRegisteredAtAsc(UUID excursionId, UUID organizationId);

    List<NonConformity> findByExcursionIdInOrderByRegisteredAtAsc(Collection<UUID> excursionIds);

    void deleteByExcursionIdIn(Collection<UUID> excursionIds);
}
