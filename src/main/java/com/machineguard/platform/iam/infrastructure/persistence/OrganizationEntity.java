package com.machineguard.platform.iam.infrastructure.persistence;

import com.machineguard.platform.iam.domain.model.OrganizationStatus;
import com.machineguard.platform.iam.domain.model.SubscriptionPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class OrganizationEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 150)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", nullable = false, length = 20)
    private SubscriptionPlan subscriptionPlan;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationStatus status;
    @Column(name = "acquisition_request_id", unique = true)
    private UUID acquisitionRequestId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationEntity() { }

    public OrganizationEntity(UUID id, String name, SubscriptionPlan subscriptionPlan,
                              OrganizationStatus status, UUID acquisitionRequestId, Instant now) {
        this(id, name, subscriptionPlan, status, acquisitionRequestId, now, now);
    }

    public OrganizationEntity(UUID id, String name, SubscriptionPlan subscriptionPlan,
                              OrganizationStatus status, UUID acquisitionRequestId,
                              Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.subscriptionPlan = subscriptionPlan;
        this.status = status;
        this.acquisitionRequestId = acquisitionRequestId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public SubscriptionPlan getSubscriptionPlan() { return subscriptionPlan; }
    public OrganizationStatus getStatus() { return status; }
    public UUID getAcquisitionRequestId() { return acquisitionRequestId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(OrganizationStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }
}
