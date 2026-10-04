package com.machineguard.platform.iam.infrastructure.persistence.repository;

import com.machineguard.platform.iam.infrastructure.persistence.AuthenticationSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthenticationSessionJpaRepository extends JpaRepository<AuthenticationSessionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthenticationSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying
    @Query("update AuthenticationSessionEntity s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
