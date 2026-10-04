package com.machineguard.platform.iam.infrastructure.persistence;

import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.domain.model.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() { }

    public UserEntity(UUID id, UUID organizationId, String email, String passwordHash, String fullName,
                      Role role, UserStatus status, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void recordLogin(Instant now) { this.lastLoginAt = now; this.updatedAt = now; }
    public void changePassword(String passwordHash, Instant now) { this.passwordHash = passwordHash; this.updatedAt = now; }
    public void assignRole(Role role, Instant now) { this.role = role; this.updatedAt = now; }
    public void setStatus(UserStatus status, Instant now) { this.status = status; this.updatedAt = now; }
}
