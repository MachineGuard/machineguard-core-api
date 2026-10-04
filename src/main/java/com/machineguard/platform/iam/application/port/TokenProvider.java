package com.machineguard.platform.iam.application.port;

import com.machineguard.platform.iam.domain.model.Role;

import java.time.Instant;
import java.util.UUID;

/** Application port for signed access tokens and secure opaque authentication tokens. */
public interface TokenProvider {
    IssuedAccessToken issueAccessToken(UUID userId, UUID organizationId, Role role, UUID sessionId);
    String newRefreshToken();
    String newInvitationToken();
    String hashToken(String rawToken);

    record IssuedAccessToken(String value, long expiresIn, Instant expiresAt) { }
}
