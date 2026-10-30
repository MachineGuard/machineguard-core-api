package com.machineguard.platform.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A machine identity with an immutable organization and explicit sensor scope. Never stores the raw credential. */
public record Gateway(UUID id, UUID organizationId, String name, String credentialHash, Set<UUID> sensorNodeIds,
                      UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
                      Instant expiresAt, Instant revokedAt) {
    public Gateway {
        Objects.requireNonNull(id); Objects.requireNonNull(organizationId);
        Objects.requireNonNull(credentialHash); Objects.requireNonNull(createdBy); Objects.requireNonNull(updatedBy);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt); Objects.requireNonNull(expiresAt);
        if (name == null || name.isBlank() || name.trim().length() > 150)
            throw new IllegalArgumentException("Gateway name must contain 1 to 150 characters");
        name = name.trim();
        sensorNodeIds = Set.copyOf(sensorNodeIds);
        if (sensorNodeIds.isEmpty() || sensorNodeIds.size() > 100)
            throw new IllegalArgumentException("A gateway requires 1 to 100 authorized sensors");
    }
    public boolean isActiveAt(Instant now) { return revokedAt == null && now.isBefore(expiresAt); }
    public Gateway rotate(String hash, Instant expiry, UUID actor, Instant now) {
        if (revokedAt != null) throw new IllegalStateException("Revoked gateways cannot rotate credentials");
        return new Gateway(id, organizationId, name, hash, sensorNodeIds, createdBy, actor, createdAt, now, expiry, null);
    }
    public Gateway revoke(UUID actor, Instant now) {
        return revokedAt != null ? this : new Gateway(id, organizationId, name, credentialHash, sensorNodeIds,
                createdBy, actor, createdAt, now, expiresAt, now);
    }
    @Override public String toString() { return "Gateway[id=" + id + ", organizationId=" + organizationId + "]"; }
}
