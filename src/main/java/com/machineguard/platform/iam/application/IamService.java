package com.machineguard.platform.iam.application;

import com.machineguard.platform.iam.application.integration.PilotRequestSubmitted;
import com.machineguard.platform.iam.domain.events.IamEvents;
import com.machineguard.platform.iam.domain.model.OrganizationStatus;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.domain.model.SubscriptionPlan;
import com.machineguard.platform.iam.domain.model.UserStatus;
import com.machineguard.platform.iam.infrastructure.persistence.AuthenticationSessionEntity;
import com.machineguard.platform.iam.infrastructure.persistence.InitialAdminInvitationEntity;
import com.machineguard.platform.iam.infrastructure.persistence.OrganizationEntity;
import com.machineguard.platform.iam.infrastructure.persistence.UserEntity;
import com.machineguard.platform.iam.infrastructure.persistence.UsedRefreshTokenEntity;
import com.machineguard.platform.iam.infrastructure.persistence.repository.AuthenticationSessionJpaRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.InitialAdminInvitationJpaRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.OrganizationJpaRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.UsedRefreshTokenJpaRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import com.machineguard.platform.iam.infrastructure.security.JwtTokenService;
import com.machineguard.platform.iam.interfaces.rest.IamResources;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.machineguard.platform.iam.infrastructure.security.JwtTokenService.tokenHash;

@Service
public class IamService {
    private final OrganizationJpaRepository organizations;
    private final UserJpaRepository users;
    private final AuthenticationSessionJpaRepository sessions;
    private final UsedRefreshTokenJpaRepository usedRefreshTokens;
    private final InitialAdminInvitationJpaRepository invitations;
    private final JwtTokenService tokens;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final long refreshTtlDays;

    public IamService(OrganizationJpaRepository organizations, UserJpaRepository users,
                      AuthenticationSessionJpaRepository sessions, UsedRefreshTokenJpaRepository usedRefreshTokens,
                      InitialAdminInvitationJpaRepository invitations, JwtTokenService tokens,
                      PasswordEncoder passwords, ApplicationEventPublisher events, Clock clock,
                      @org.springframework.beans.factory.annotation.Value("${machineguard.iam.refresh-token-ttl-days:30}") long refreshTtlDays) {
        this.organizations = organizations;
        this.users = users;
        this.sessions = sessions;
        this.usedRefreshTokens = usedRefreshTokens;
        this.invitations = invitations;
        this.tokens = tokens;
        this.passwords = passwords;
        this.events = events;
        this.clock = clock;
        this.refreshTtlDays = refreshTtlDays;
        if (refreshTtlDays < 1 || refreshTtlDays > 90) {
            throw new IllegalStateException("IAM refresh-token TTL must be between 1 and 90 days");
        }
    }

    @Transactional
    public AuthenticationResult login(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        UserEntity user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> unauthorized("Invalid email or password"));
        OrganizationEntity organization = organizations.findById(user.getOrganizationId())
                .orElseThrow(() -> unauthorized("Invalid email or password"));
        if (!passwords.matches(password, user.getPasswordHash()) || user.getStatus() != UserStatus.ACTIVE
                || organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw unauthorized("Invalid email or password");
        }

        Instant now = clock.instant();
        user.recordLogin(now);
        UUID sessionId = UUID.randomUUID();
        String refreshToken = tokens.newRefreshToken();
        sessions.save(new AuthenticationSessionEntity(sessionId, user.getId(), tokenHash(refreshToken), now,
                now.plusSeconds(refreshTtlDays * 86_400)));
        events.publishEvent(new IamEvents.UserLoggedIn(user.getId(), organization.getId(), sessionId, now));
        return authentication(user, organization, sessionId, refreshToken);
    }

    @Transactional(noRollbackFor = RefreshTokenReplayException.class)
    public AuthenticationResult refresh(String rawRefreshToken) {
        String hash = tokenHash(rawRefreshToken);
        Instant now = clock.instant();
        var session = sessions.findByRefreshTokenHash(hash);
        if (session.isEmpty()) {
            var reused = usedRefreshTokens.findById(hash);
            if (reused.isPresent()) {
                sessions.findById(reused.get().getSessionId()).ifPresent(s -> s.revoke(now));
                throw new RefreshTokenReplayException();
            }
            throw unauthorized("Invalid or expired refresh token");
        }
        AuthenticationSessionEntity current = session.get();
        if (!current.isActiveAt(now)) throw unauthorized("Invalid or expired refresh token");
        UserEntity user = users.findById(current.getUserId()).orElseThrow(() -> unauthorized("Invalid refresh token"));
        OrganizationEntity organization = organizations.findById(user.getOrganizationId())
                .orElseThrow(() -> unauthorized("Invalid refresh token"));
        if (user.getStatus() != UserStatus.ACTIVE || organization.getStatus() != OrganizationStatus.ACTIVE) {
            current.revoke(now);
            throw unauthorized("Invalid or expired refresh token");
        }

        String nextToken = tokens.newRefreshToken();
        usedRefreshTokens.save(new UsedRefreshTokenEntity(hash, current.getId(), now));
        current.rotate(tokenHash(nextToken), now, now.plusSeconds(refreshTtlDays * 86_400));
        return authentication(user, organization, current.getId(), nextToken);
    }

    @Transactional
    public void logout(IamPrincipal principal, String rawRefreshToken) {
        Instant now = clock.instant();
        AuthenticationSessionEntity session = sessions.findByRefreshTokenHash(tokenHash(rawRefreshToken))
                .orElseThrow(() -> unauthorized("Invalid or expired refresh token"));
        if (!session.getUserId().equals(principal.userId())) throw unauthorized("Invalid or expired refresh token");
        session.revoke(now);
        events.publishEvent(new IamEvents.UserLoggedOut(principal.userId(), session.getId(), now));
    }

    @Transactional
    public void changePassword(IamPrincipal principal, String currentPassword, String newPassword) {
        UserEntity user = users.findById(principal.userId()).orElseThrow(() -> notFound("User not found"));
        if (!passwords.matches(currentPassword, user.getPasswordHash())) throw unauthorized("Current password is incorrect");
        validatePassword(newPassword);
        Instant now = clock.instant();
        user.changePassword(passwords.encode(newPassword), now);
        sessions.revokeAllByUserId(user.getId(), now);
        events.publishEvent(new IamEvents.PasswordReset(user.getId(), now));
    }

    @Transactional
    public IamResources.InvitationAcceptedResource acceptInvitation(String rawToken, String newPassword) {
        validatePassword(newPassword);
        Instant now = clock.instant();
        InitialAdminInvitationEntity invitation = invitations.findByTokenHash(tokenHash(rawToken))
                .filter(i -> i.isUsableAt(now)).orElseThrow(() -> unauthorized("Invalid or expired invitation"));
        UserEntity user = users.findById(invitation.getUserId()).orElseThrow(() -> unauthorized("Invalid invitation"));
        OrganizationEntity organization = organizations.findById(user.getOrganizationId())
                .orElseThrow(() -> unauthorized("Invalid invitation"));
        if (user.getStatus() != UserStatus.INACTIVE || organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw unauthorized("Invalid or expired invitation");
        }
        user.changePassword(passwords.encode(newPassword), now);
        user.setStatus(UserStatus.ACTIVE, now);
        invitation.consume(now);
        events.publishEvent(new IamEvents.UserStatusChanged(user.getId(), user.getOrganizationId(), UserStatus.ACTIVE, now));
        return new IamResources.InvitationAcceptedResource("Invitation accepted. You can now sign in.");
    }

    @Transactional(readOnly = true)
    public IamResources.UserResource profile(IamPrincipal principal) {
        UserEntity user = users.findByIdAndOrganizationId(principal.userId(), principal.organizationId())
                .orElseThrow(() -> notFound("User not found"));
        return userResource(user);
    }

    @Transactional
    public IamResources.UserResource registerUser(IamPrincipal actor, UUID organizationId,
                                                  String email, String password, String fullName, Role role) {
        ensureSameTenant(actor, organizationId);
        OrganizationEntity organization = lockOrganizationForAdminChange(actor);
        validatePassword(password);
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmailIgnoreCase(normalizedEmail)) throw conflict("Email is already registered");
        Instant now = clock.instant();
        UserEntity user = users.save(new UserEntity(UUID.randomUUID(), organization.getId(), normalizedEmail,
                passwords.encode(password), fullName.trim(), role, UserStatus.ACTIVE, now));
        events.publishEvent(new IamEvents.UserRegistered(user.getId(), organizationId, normalizedEmail, role, now));
        if (role != Role.VIEWER) events.publishEvent(new IamEvents.RoleAssigned(user.getId(), organizationId, role, now));
        return userResource(user);
    }

    @Transactional
    public IamResources.UserResource assignRole(IamPrincipal actor, UUID userId, Role role) {
        lockOrganizationForAdminChange(actor);
        UserEntity target = userInTenant(userId, actor.organizationId());
        if (target.getRole() == Role.ADMIN && role != Role.ADMIN && target.getStatus() == UserStatus.ACTIVE
                && activeAdminCount(actor.organizationId()) <= 1) {
            throw conflict("The last active administrator cannot be demoted");
        }
        if (target.getRole() != role) {
            target.assignRole(role, clock.instant());
            events.publishEvent(new IamEvents.RoleAssigned(target.getId(), target.getOrganizationId(), role, clock.instant()));
        }
        return userResource(target);
    }

    @Transactional
    public IamResources.UserResource changeUserStatus(IamPrincipal actor, UUID userId, UserStatus status) {
        lockOrganizationForAdminChange(actor);
        UserEntity target = userInTenant(userId, actor.organizationId());
        if (target.getStatus() == UserStatus.ACTIVE && status == UserStatus.INACTIVE && target.getRole() == Role.ADMIN
                && activeAdminCount(actor.organizationId()) <= 1) {
            throw conflict("The last active administrator cannot be deactivated");
        }
        if (target.getStatus() != status) {
            Instant now = clock.instant();
            target.setStatus(status, now);
            if (status == UserStatus.INACTIVE) sessions.revokeAllByUserId(target.getId(), now);
            events.publishEvent(new IamEvents.UserStatusChanged(target.getId(), target.getOrganizationId(), status, now));
        }
        return userResource(target);
    }

    @Transactional(readOnly = true)
    public IamResources.OrganizationResource organization(IamPrincipal actor, UUID organizationId) {
        ensureSameTenant(actor, organizationId);
        return organizations.findById(organizationId).map(IamService::organizationResource)
                .orElseThrow(() -> notFound("Organization not found"));
    }

    @Transactional(readOnly = true)
    public IamResources.TokenValidationResource validateToken(IamPrincipal principal) {
        UserEntity user = users.findByIdAndOrganizationId(principal.userId(), principal.organizationId())
                .orElseThrow(() -> unauthorized("Invalid token"));
        OrganizationEntity organization = organizations.findById(user.getOrganizationId())
                .orElseThrow(() -> unauthorized("Invalid token"));
        AuthenticationSessionEntity session = sessions.findById(principal.sessionId())
                .orElseThrow(() -> unauthorized("Invalid token"));
        if (user.getStatus() != UserStatus.ACTIVE || organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw unauthorized("Invalid token");
        }
        if (!session.getUserId().equals(user.getId()) || !session.isActiveAt(clock.instant()) || user.getRole() != principal.role()) {
            throw unauthorized("Invalid token");
        }
        return new IamResources.TokenValidationResource(true, user.getId(), organization.getId(), user.getRole(),
                principal.expiresAt());
    }

    @Transactional
    public void handlePilotRequest(PilotRequestSubmitted request) {
        if (request == null || request.requestId() == null) throw new IllegalArgumentException("Pilot request id is required");
        if (request.companyName() == null || request.companyName().isBlank()
                || request.contactName() == null || request.contactName().isBlank()
                || request.contactEmail() == null || request.contactEmail().isBlank()) {
            throw new IllegalArgumentException("Pilot request company and contact fields are required");
        }
        if (organizations.findByAcquisitionRequestId(request.requestId()).isPresent()) return;
        String email = normalizeEmail(request.contactEmail());
        if (users.existsByEmailIgnoreCase(email)) throw conflict("Pilot contact email is already registered");
        Instant now = clock.instant();
        UUID organizationId = UUID.randomUUID();
        OrganizationEntity organization = organizations.save(new OrganizationEntity(organizationId,
                request.companyName().trim(), SubscriptionPlan.FREE, OrganizationStatus.ACTIVE, request.requestId(), now));
        String unusablePassword = tokens.newRefreshToken() + "x";
        UserEntity admin = users.save(new UserEntity(UUID.randomUUID(), organizationId, email,
                passwords.encode(unusablePassword), request.contactName().trim(), Role.ADMIN, UserStatus.INACTIVE, now));
        String invitationToken = tokens.newInvitationToken();
        UUID invitationId = UUID.randomUUID();
        Instant expiresAt = now.plusSeconds(86_400);
        invitations.save(new InitialAdminInvitationEntity(invitationId, admin.getId(), tokenHash(invitationToken), now, expiresAt));
        events.publishEvent(new IamEvents.OrganizationCreated(organization.getId(), organization.getName(), now));
        events.publishEvent(new IamEvents.UserRegistered(admin.getId(), organizationId, email, Role.ADMIN, now));
        events.publishEvent(new IamEvents.RoleAssigned(admin.getId(), organizationId, Role.ADMIN, now));
        events.publishEvent(new IamEvents.InitialAdminInvitationIssued(invitationId, email, invitationToken, expiresAt));
    }

    public IamResources.UserResource userResource(UserEntity user) {
        return new IamResources.UserResource(user.getId(), user.getOrganizationId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.getStatus(), user.getLastLoginAt(), user.getCreatedAt());
    }

    public static IamResources.OrganizationResource organizationResource(OrganizationEntity organization) {
        return new IamResources.OrganizationResource(organization.getId(), organization.getName(),
                organization.getSubscriptionPlan(), organization.getStatus(), organization.getCreatedAt(), organization.getUpdatedAt());
    }

    private AuthenticationResult authentication(UserEntity user, OrganizationEntity organization,
                                                UUID sessionId, String refreshToken) {
        JwtTokenService.IssuedAccessToken access = tokens.issueAccessToken(user.getId(), organization.getId(), user.getRole(), sessionId);
        return new AuthenticationResult(access, refreshToken, userResource(user), organizationResource(organization));
    }

    private UserEntity userInTenant(UUID userId, UUID organizationId) {
        return users.findByIdAndOrganizationId(userId, organizationId).orElseThrow(() -> notFound("User not found"));
    }

    private long activeAdminCount(UUID organizationId) {
        return users.countByOrganizationIdAndRoleAndStatus(organizationId, Role.ADMIN, UserStatus.ACTIVE);
    }

    private OrganizationEntity lockOrganizationForAdminChange(IamPrincipal actor) {
        if (actor.role() != Role.ADMIN) throw new AccessDeniedException("An active organization administrator is required");
        OrganizationEntity organization = organizations.findByIdForUpdate(actor.organizationId())
                .filter(o -> o.getStatus() == OrganizationStatus.ACTIVE)
                .orElseThrow(() -> conflict("Organization is suspended or unavailable"));
        UserEntity currentActor = users.findByIdAndOrganizationId(actor.userId(), actor.organizationId())
                .orElseThrow(() -> new AccessDeniedException("An active organization administrator is required"));
        if (currentActor.getRole() != Role.ADMIN || currentActor.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("An active organization administrator is required");
        }
        return organization;
    }

    private static void ensureSameTenant(IamPrincipal actor, UUID organizationId) {
        if (!actor.organizationId().equals(organizationId)) throw new AccessDeniedException("Organization does not match authenticated tenant");
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static void validatePassword(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must contain at least 12 characters and no more than 72 UTF-8 bytes");
        }
    }

    private static ResponseStatusException unauthorized(String reason) { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason); }
    private static ResponseStatusException notFound(String reason) { return new ResponseStatusException(HttpStatus.NOT_FOUND, reason); }
    private static ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }

    public record AuthenticationResult(JwtTokenService.IssuedAccessToken accessToken, String refreshToken,
                                       IamResources.UserResource user, IamResources.OrganizationResource organization) { }
}
