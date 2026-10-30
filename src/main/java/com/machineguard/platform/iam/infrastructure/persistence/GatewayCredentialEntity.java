package com.machineguard.platform.iam.infrastructure.persistence;

import com.machineguard.platform.iam.domain.model.Gateway;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "gateway_credentials")
public class GatewayCredentialEntity {
    @Id private UUID id;
    @Column(name="organization_id", nullable=false, updatable=false) private UUID organizationId;
    @Column(nullable=false, length=150) private String name;
    @Column(name="credential_hash", nullable=false, length=64) private String credentialHash;
    @Column(name="created_by", nullable=false, updatable=false) private UUID createdBy;
    @Column(name="updated_by", nullable=false) private UUID updatedBy;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Column(name="expires_at", nullable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
    @ElementCollection
    @CollectionTable(name="gateway_sensor_nodes", joinColumns=@JoinColumn(name="gateway_id"))
    @Column(name="sensor_node_id", nullable=false)
    private Set<UUID> sensorNodeIds = new HashSet<>();
    protected GatewayCredentialEntity() { }
    public GatewayCredentialEntity(Gateway g) {
        id=g.id(); organizationId=g.organizationId(); name=g.name(); credentialHash=g.credentialHash();
        createdBy=g.createdBy(); updatedBy=g.updatedBy(); createdAt=g.createdAt(); updatedAt=g.updatedAt();
        expiresAt=g.expiresAt(); revokedAt=g.revokedAt(); sensorNodeIds=new HashSet<>(g.sensorNodeIds());
    }
    public Gateway toDomain() {
        return new Gateway(id, organizationId, name, credentialHash, sensorNodeIds, createdBy, updatedBy,
                createdAt, updatedAt, expiresAt, revokedAt);
    }
}
