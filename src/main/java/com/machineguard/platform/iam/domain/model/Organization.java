package com.machineguard.platform.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Domain aggregate for a tenant organization. Persistence concerns belong to adapters. */
public final class Organization {
    private final UUID id;
    private final String name;
    private final SubscriptionPlan subscriptionPlan;
    private OrganizationStatus status;
    private final UUID acquisitionRequestId;
    private final Instant createdAt;
    private Instant updatedAt;

    private Organization(UUID id, String name, SubscriptionPlan subscriptionPlan, OrganizationStatus status,
                         UUID acquisitionRequestId, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "organization id is required");
        if (name == null || name.isBlank() || name.trim().length() > 150) {
            throw new IllegalArgumentException("Organization name must contain 1 to 150 characters");
        }
        this.name = name.trim();
        this.subscriptionPlan = Objects.requireNonNull(subscriptionPlan, "subscription plan is required");
        this.status = Objects.requireNonNull(status, "organization status is required");
        this.acquisitionRequestId = acquisitionRequestId;
        this.createdAt = Objects.requireNonNull(createdAt, "creation time is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "update time is required");
    }

    public static Organization create(UUID id, String name, SubscriptionPlan plan, OrganizationStatus status,
                                      UUID acquisitionRequestId, Instant now) {
        return new Organization(id, name, plan, status, acquisitionRequestId, now, now);
    }

    public static Organization restore(UUID id, String name, SubscriptionPlan plan, OrganizationStatus status,
                                       UUID acquisitionRequestId, Instant createdAt, Instant updatedAt) {
        return new Organization(id, name, plan, status, acquisitionRequestId, createdAt, updatedAt);
    }

    public void changeStatus(OrganizationStatus status, Instant now) {
        this.status = Objects.requireNonNull(status, "organization status is required");
        this.updatedAt = Objects.requireNonNull(now, "update time is required");
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public SubscriptionPlan getSubscriptionPlan() { return subscriptionPlan; }
    public OrganizationStatus getStatus() { return status; }
    public UUID getAcquisitionRequestId() { return acquisitionRequestId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
