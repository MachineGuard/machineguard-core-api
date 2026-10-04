package com.machineguard.platform.iam.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "iam_bootstrap_state")
public class BootstrapStateEntity {
    @Id
    @Column(name = "bootstrap_key", length = 40)
    private String bootstrapKey;
    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected BootstrapStateEntity() { }
    public BootstrapStateEntity(String bootstrapKey, Instant completedAt) {
        this.bootstrapKey = bootstrapKey;
        this.completedAt = completedAt;
    }
    public String getBootstrapKey() { return bootstrapKey; }
    public Instant getCompletedAt() { return completedAt; }
}
