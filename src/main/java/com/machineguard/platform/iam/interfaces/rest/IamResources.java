package com.machineguard.platform.iam.interfaces.rest;

import com.machineguard.platform.iam.domain.model.OrganizationStatus;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.domain.model.SubscriptionPlan;
import com.machineguard.platform.iam.domain.model.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class IamResources {
    private IamResources() { }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 72) String password) {
        public LoginRequest { if (email != null) email = email.trim(); }
    }
    public record RefreshRequest(@NotBlank String refreshToken) { }
    public record LogoutRequest(@NotBlank String refreshToken) { }
    public record AcceptInvitationRequest(@NotBlank String token,
                                          @NotBlank @Size(min = 12, max = 72) String password) { }
    public record ChangePasswordRequest(@NotBlank @Size(max = 72) String currentPassword,
                                        @NotBlank @Size(min = 12, max = 72) String newPassword) { }
    public record RegisterUserRequest(@NotBlank @Email @Size(max = 254) String email,
                                      @NotBlank @Size(max = 72) String password,
                                      @NotBlank @Size(max = 150) String fullName,
                                      @NotNull Role role) {
        public RegisterUserRequest { if (email != null) email = email.trim(); }
    }
    public record AssignRoleRequest(@NotNull Role role) { }
    public record ChangeUserStatusRequest(@NotNull UserStatus status) { }

    public record UserResource(UUID id, UUID organizationId, String email, String fullName, Role role,
                               UserStatus status, Instant lastLoginAt, Instant createdAt) { }
    public record OrganizationResource(UUID id, String name, SubscriptionPlan subscriptionPlan,
                                       OrganizationStatus status, Instant createdAt, Instant updatedAt) { }
    public record AuthenticationResource(String tokenType, String accessToken, long expiresIn,
                                         String refreshToken, UserResource user,
                                         OrganizationResource organization) { }
    public record TokenValidationResource(boolean valid, UUID userId, UUID organizationId, Role role,
                                          Instant expiresAt) { }
    public record InvitationAcceptedResource(String message) { }
    public record MessageResource(String message) { }
}
