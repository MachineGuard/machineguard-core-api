package com.machineguard.platform.iam.infrastructure.persistence.repository;

import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.domain.model.UserStatus;
import com.machineguard.platform.iam.infrastructure.persistence.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    Optional<UserEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByEmailIgnoreCase(String email);
    long countByOrganizationIdAndRoleAndStatus(UUID organizationId, Role role, UserStatus status);
}
