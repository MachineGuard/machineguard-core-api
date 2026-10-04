package com.machineguard.platform.iam.infrastructure.persistence.repository;

import com.machineguard.platform.iam.infrastructure.persistence.InitialAdminInvitationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface InitialAdminInvitationJpaRepository extends JpaRepository<InitialAdminInvitationEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InitialAdminInvitationEntity> findByTokenHash(String tokenHash);
}
