package com.machineguard.platform.iam.infrastructure.persistence.repository;

import com.machineguard.platform.iam.infrastructure.persistence.OrganizationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationJpaRepository extends JpaRepository<OrganizationEntity, UUID> {
    Optional<OrganizationEntity> findByAcquisitionRequestId(UUID acquisitionRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrganizationEntity o where o.id = :id")
    Optional<OrganizationEntity> findByIdForUpdate(@Param("id") UUID id);
}
