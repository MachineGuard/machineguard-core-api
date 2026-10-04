package com.machineguard.platform.iam.domain.model;

import java.time.Instant;
import java.util.Objects;

public record BootstrapState(String key, Instant completedAt) {
    public BootstrapState {
        Objects.requireNonNull(key, "bootstrap key is required");
        Objects.requireNonNull(completedAt, "completion time is required");
    }
}
