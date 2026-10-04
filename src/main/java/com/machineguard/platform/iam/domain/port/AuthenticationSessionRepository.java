package com.machineguard.platform.iam.domain.port;

import com.machineguard.platform.iam.domain.model.AuthenticationSession;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthenticationSessionRepository {
    Optional<AuthenticationSession> findById(UUID id);
    Optional<AuthenticationSession> findByRefreshTokenHash(String tokenHash);
    AuthenticationSession save(AuthenticationSession session);
    int revokeAllByUserId(UUID userId, Instant now);
}
