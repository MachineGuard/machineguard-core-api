package com.machineguard.platform.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AuthenticationSession {
    private final UUID id;
    private final UUID userId;
    private String refreshTokenHash;
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant revokedAt;

    private AuthenticationSession(UUID id, UUID userId, String refreshTokenHash, Instant issuedAt,
                                  Instant expiresAt, Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "session id is required");
        this.userId = Objects.requireNonNull(userId, "user id is required");
        this.refreshTokenHash = Objects.requireNonNull(refreshTokenHash, "refresh-token hash is required");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issue time is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiry time is required");
        this.revokedAt = revokedAt;
    }

    public static AuthenticationSession start(UUID id, UUID userId, String refreshTokenHash,
                                              Instant issuedAt, Instant expiresAt) {
        return new AuthenticationSession(id, userId, refreshTokenHash, issuedAt, expiresAt, null);
    }

    public static AuthenticationSession restore(UUID id, UUID userId, String refreshTokenHash, Instant issuedAt,
                                                Instant expiresAt, Instant revokedAt) {
        return new AuthenticationSession(id, userId, refreshTokenHash, issuedAt, expiresAt, revokedAt);
    }

    public boolean isActiveAt(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }

    public void rotate(String newRefreshTokenHash, Instant now, Instant newExpiry) {
        if (!isActiveAt(now)) throw new IllegalStateException("Only an active session can be rotated");
        this.refreshTokenHash = Objects.requireNonNull(newRefreshTokenHash, "refresh-token hash is required");
        this.issuedAt = Objects.requireNonNull(now, "issue time is required");
        this.expiresAt = Objects.requireNonNull(newExpiry, "expiry time is required");
        this.revokedAt = null;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) revokedAt = Objects.requireNonNull(now, "revocation time is required");
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
