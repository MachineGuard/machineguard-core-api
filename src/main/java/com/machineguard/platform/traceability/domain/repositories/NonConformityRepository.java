package com.machineguard.platform.traceability.domain.repositories;

import com.machineguard.platform.traceability.domain.model.NonConformity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NonConformityRepository extends JpaRepository<NonConformity, Long> {

    Optional<NonConformity> findByIdAndOrganizationId(Long id, Long organizationId);

    List<NonConformity> findByExcursionIdAndOrganizationIdOrderByRegisteredAtAsc(Long excursionId, Long organizationId);

    List<NonConformity> findByExcursionIdInOrderByRegisteredAtAsc(Collection<Long> excursionIds);

    void deleteByExcursionIdIn(Collection<Long> excursionIds);
}
