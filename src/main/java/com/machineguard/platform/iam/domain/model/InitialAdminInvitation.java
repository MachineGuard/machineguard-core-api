package com.machineguard.platform.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class InitialAdminInvitation {
    private final UUID id;
    private final UUID userId;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant consumedAt;

    private InitialAdminInvitation(UUID id, UUID userId, String tokenHash, Instant issuedAt,
                                   Instant expiresAt, Instant consumedAt) {
        this.id = Objects.requireNonNull(id, "invitation id is required");
        this.userId = Objects.requireNonNull(userId, "user id is required");
        this.tokenHash = Objects.requireNonNull(tokenHash, "token hash is required");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issue time is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiry time is required");
        this.consumedAt = consumedAt;
    }

    public static InitialAdminInvitation issue(UUID id, UUID userId, String tokenHash,
                                               Instant issuedAt, Instant expiresAt) {
        return new InitialAdminInvitation(id, userId, tokenHash, issuedAt, expiresAt, null);
    }

    public static InitialAdminInvitation restore(UUID id, UUID userId, String tokenHash, Instant issuedAt,
                                                 Instant expiresAt, Instant consumedAt) {
        return new InitialAdminInvitation(id, userId, tokenHash, issuedAt, expiresAt, consumedAt);
    }

    public boolean isUsableAt(Instant now) { return consumedAt == null && expiresAt.isAfter(now); }

    public void consume(Instant now) {
        if (!isUsableAt(now)) throw new IllegalStateException("Invitation is expired or already consumed");
        consumedAt = Objects.requireNonNull(now, "consumption time is required");
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
}
