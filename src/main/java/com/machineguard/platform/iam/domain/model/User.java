package com.machineguard.platform.iam.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Domain aggregate for an organization member and its access policy. */
public final class User {
    private final UUID id;
    private final UUID organizationId;
    private final String email;
    private String passwordHash;
    private final String fullName;
    private Role role;
    private UserStatus status;
    private Instant lastLoginAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UUID id, UUID organizationId, String email, String passwordHash, String fullName, Role role,
                 UserStatus status, Instant lastLoginAt, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "user id is required");
        this.organizationId = Objects.requireNonNull(organizationId, "organization id is required");
        if (email == null || email.isBlank() || email.length() > 254) {
            throw new IllegalArgumentException("User email must contain 1 to 254 characters");
        }
        this.email = email;
        this.passwordHash = Objects.requireNonNull(passwordHash, "password hash is required");
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 150) {
            throw new IllegalArgumentException("User name must contain 1 to 150 characters");
        }
        this.fullName = fullName.trim();
        this.role = Objects.requireNonNull(role, "user role is required");
        this.status = Objects.requireNonNull(status, "user status is required");
        this.lastLoginAt = lastLoginAt;
        this.createdAt = Objects.requireNonNull(createdAt, "creation time is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "update time is required");
    }

    public static User register(UUID id, UUID organizationId, String email, String passwordHash, String fullName,
                                Role role, UserStatus status, Instant now) {
        return new User(id, organizationId, email, passwordHash, fullName, role, status, null, now, now);
    }

    public static User restore(UUID id, UUID organizationId, String email, String passwordHash, String fullName,
                               Role role, UserStatus status, Instant lastLoginAt, Instant createdAt, Instant updatedAt) {
        return new User(id, organizationId, email, passwordHash, fullName, role, status, lastLoginAt, createdAt, updatedAt);
    }

    public void recordLogin(Instant now) {
        this.lastLoginAt = Objects.requireNonNull(now, "login time is required");
        this.updatedAt = now;
    }

    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "password hash is required");
        this.updatedAt = Objects.requireNonNull(now, "update time is required");
    }

    public void assignRole(Role role, Instant now) {
        this.role = Objects.requireNonNull(role, "user role is required");
        this.updatedAt = Objects.requireNonNull(now, "update time is required");
    }

    public void changeStatus(UserStatus status, Instant now) {
        this.status = Objects.requireNonNull(status, "user status is required");
        this.updatedAt = Objects.requireNonNull(now, "update time is required");
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
}
