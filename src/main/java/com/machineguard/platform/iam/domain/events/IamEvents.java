package com.machineguard.platform.iam.domain.events;

import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.domain.model.UserStatus;

import java.time.Instant;
import java.util.UUID;

public final class IamEvents {
    private IamEvents() { }

    public record OrganizationCreated(UUID organizationId, String name, Instant occurredAt) { }
    public record OrganizationStatusChanged(UUID organizationId, String status, Instant occurredAt) { }
    public record UserRegistered(UUID userId, UUID organizationId, String email, Role role, Instant occurredAt) { }
    public record UserLoggedIn(UUID userId, UUID organizationId, UUID sessionId, Instant occurredAt) { }
    public record UserLoggedOut(UUID userId, UUID sessionId, Instant occurredAt) { }
    public record PasswordReset(UUID userId, Instant occurredAt) { }
    public record RoleAssigned(UUID userId, UUID organizationId, Role role, Instant occurredAt) { }
    public record UserStatusChanged(UUID userId, UUID organizationId, UserStatus status, Instant occurredAt) { }
    public record InitialAdminInvitationIssued(UUID invitationId, String email, String rawToken, Instant expiresAt) { }
}
