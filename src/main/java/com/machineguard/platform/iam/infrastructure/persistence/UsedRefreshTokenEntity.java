package com.machineguard.platform.iam.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "authentication_refresh_token_history")
public class UsedRefreshTokenEntity {
    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    protected UsedRefreshTokenEntity() { }
    public UsedRefreshTokenEntity(String tokenHash, UUID sessionId, Instant usedAt) {
        this.tokenHash = tokenHash;
        this.sessionId = sessionId;
        this.usedAt = usedAt;
    }
    public String getTokenHash() { return tokenHash; }
    public UUID getSessionId() { return sessionId; }
    public Instant getUsedAt() { return usedAt; }
}
