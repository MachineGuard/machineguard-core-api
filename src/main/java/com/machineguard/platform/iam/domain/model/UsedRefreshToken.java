package com.machineguard.platform.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Audit record used to detect replay of a previously rotated refresh token. */
public record UsedRefreshToken(String tokenHash, UUID sessionId, Instant usedAt) {
    public UsedRefreshToken {
        Objects.requireNonNull(tokenHash, "token hash is required");
        Objects.requireNonNull(sessionId, "session id is required");
        Objects.requireNonNull(usedAt, "use time is required");
    }
}
