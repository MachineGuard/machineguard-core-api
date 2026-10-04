package com.machineguard.platform.iam.application.port;

import com.machineguard.platform.iam.domain.model.Role;

import java.time.Instant;
import java.util.UUID;

public interface TokenProvider {
    IssuedAccessToken issueAccessToken(UUID userId, UUID organizationId, Role role, UUID sessionId);
    String newRefreshToken();
    String newInvitationToken();
    /** Devuelve el hash SHA-256 que se guarda para tokens de refresh e invitación. */
    String hashToken(String rawToken);

    record IssuedAccessToken(String value, long expiresIn, Instant expiresAt) { }
}
