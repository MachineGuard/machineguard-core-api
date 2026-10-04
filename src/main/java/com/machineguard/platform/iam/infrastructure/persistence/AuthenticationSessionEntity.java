package com.machineguard.platform.iam.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "authentication_sessions")
public class AuthenticationSessionEntity {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AuthenticationSessionEntity() { }

    public AuthenticationSessionEntity(UUID id, UUID userId, String hash, Instant issuedAt, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.refreshTokenHash = hash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isActiveAt(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void rotate(String hash, Instant now, Instant newExpiry) {
        this.refreshTokenHash = hash;
        this.issuedAt = now;
        this.expiresAt = newExpiry;
        this.revokedAt = null;
    }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
}
