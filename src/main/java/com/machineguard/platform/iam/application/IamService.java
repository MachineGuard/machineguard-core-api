package com.machineguard.platform.iam.application;

import com.machineguard.platform.iam.application.integration.PilotRequestSubmitted;
import com.machineguard.platform.iam.application.port.PasswordHasher;
import com.machineguard.platform.iam.application.port.TokenProvider;
import com.machineguard.platform.iam.domain.events.IamEvents;
import com.machineguard.platform.iam.domain.model.AuthenticationSession;
import com.machineguard.platform.iam.domain.model.InitialAdminInvitation;
import com.machineguard.platform.iam.domain.model.Organization;
import com.machineguard.platform.iam.domain.model.OrganizationStatus;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.domain.model.SubscriptionPlan;
import com.machineguard.platform.iam.domain.model.UsedRefreshToken;
import com.machineguard.platform.iam.domain.model.User;
import com.machineguard.platform.iam.domain.model.UserStatus;
import com.machineguard.platform.iam.domain.port.AuthenticationSessionRepository;
import com.machineguard.platform.iam.domain.port.InitialAdminInvitationRepository;
import com.machineguard.platform.iam.domain.port.OrganizationRepository;
import com.machineguard.platform.iam.domain.port.UsedRefreshTokenRepository;
import com.machineguard.platform.iam.domain.port.UserRepository;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import com.machineguard.platform.iam.interfaces.rest.IamResources;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class IamService {
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final AuthenticationSessionRepository sessions;
    private final UsedRefreshTokenRepository usedRefreshTokens;
    private final InitialAdminInvitationRepository invitations;
    private final TokenProvider tokens;
    private final PasswordHasher passwords;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final long refreshTtlDays;

    public IamService(OrganizationRepository organizations, UserRepository users,
                      AuthenticationSessionRepository sessions, UsedRefreshTokenRepository usedRefreshTokens,
                      InitialAdminInvitationRepository invitations, TokenProvider tokens,
                      PasswordHasher passwords, ApplicationEventPublisher events, Clock clock,
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
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> unauthorized("Invalid email or password"));
        Organization organization = organizations.findById(user.getOrganizationId())
                .orElseThrow(() -> unauthorized("Invalid email or password"));
        if (!passwords.matches(password, user.getPasswordHash()) || user.getStatus() != UserStatus.ACTIVE
                || organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw unauthorized("Invalid email or password");
        }

        Instant now = clock.instant();
        user.recordLogin(now);
        users.save(user);
        UUID sessionId = UUID.randomUUID();
        String refreshToken = tokens.newRefreshToken();
        sessions.save(AuthenticationSession.start(sessionId, user.getId(), tokens.hashToken(refreshToken), now,
                now.plusSeconds(refreshTtlDays * 86_400)));
        events.publishEvent(new IamEvents.UserLoggedIn(user.getId(), organization.getId(), sessionId, now));
        return authentication(user, organization, sessionId, refreshToken);
    }

    // Conserva la revocación de la sesión cuando se reutiliza un token y se responde con 401.
    @Transactional(noRollbackFor = RefreshTokenReplayException.class)
    public AuthenticationResult refresh(String rawRefreshToken) {
        String hash = tokens.hashToken(rawRefreshToken);
        Instant now = clock.instant();
        var session = sessions.findByRefreshTokenHash(hash);
        if (session.isEmpty()) {
            var reused = usedRefreshTokens.findByTokenHash(hash);
            if (reused.isPresent()) {
                sessions.findById(reused.get().sessionId()).ifPresent(s -> {
                    s.revoke(now);
                    sessions.save(s);
                });
                throw new RefreshTokenReplayException();
            }
            throw unauthorized("Invalid or expired refresh token");
        }
        AuthenticationSession current = session.get();
        if (!current.isActiveAt(now)) throw unauthorized("Invalid or expired refresh token");
        User user = users.findById(current.getUserId()).orElseThrow(() -> unauthorized("Invalid refresh token"));
        Organization organization = organizations.findById(user.getOrganizationId())
                .orElseThrow(() -> unauthorized("Invalid refresh token"));
        if (user.getStatus() != UserStatus.ACTIVE || organization.getStatus() != OrganizationStatus.ACTIVE) {
            current.revoke(now);
            throw unauthorized("Invalid or expired refresh token");
        }

        String nextToken = tokens.newRefreshToken();
        usedRefreshTokens.save(new UsedRefreshToken(hash, current.getId(), now));
        current.rotate(tokens.hashToken(nextToken), now, now.plusSeconds(refreshTtlDays * 86_400));
        sessions.save(current);
        return authentication(user, organization, current.getId(), nextToken);
    }

    @Transactional
    public void logout(IamPrincipal principal, String rawRefreshToken) {
        Instant now = clock.instant();
        AuthenticationSession session = sessions.findByRefreshTokenHash(tokens.hashToken(rawRefreshToken))
                .orElseThrow(() -> unauthorized("Invalid or expired refresh token"));
        if (!session.getUserId().equals(principal.userId())) throw unauthorized("Invalid or expired refresh token");
        session.revoke(now);
        sessions.save(session);
        events.publishEvent(new IamEvents.UserLoggedOut(principal.userId(), session.getId(), now));
    }

    @Transactional
    public void changePassword(IamPrincipal principal, String currentPassword, String newPassword) {
        User user = users.findById(principal.userId()).orElseThrow(() -> notFound("User not found"));
        if (!passwords.matches(currentPassword, user.getPasswordHash())) throw unauthorized("Current password is incorrect");
        validatePassword(newPassword);
        Instant now = clock.instant();
        user.changePassword(passwords.hash(newPassword), now);
        users.save(user);
        sessions.revokeAllByUserId(user.getId(), now);
        events.publishEvent(new IamEvents.PasswordReset(user.getId(), now));
    }

    @Transactional
    public IamResources.InvitationAcceptedResource acceptInvitation(String rawToken, String newPassword) {
        validatePassword(newPassword);
        Instant now = clock.instant();
        InitialAdminInvitation invitation = invitations.findByTokenHash(tokens.hashToken(rawToken))
                .filter(i -> i.isUsableAt(now)).orElseThrow(() -> unauthorized("Invalid or expired invitation"));
        User user = users.findById(invitation.getUserId()).orElseThrow(() -> unauthorized("Invalid invitation"));
        Organization organization = organizations.findById(user.getOrganizationId())
                .orElseThrow(() -> unauthorized("Invalid invitation"));
        if (user.getStatus() != UserStatus.INACTIVE || organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw unauthorized("Invalid or expired invitation");
        }
        user.changePassword(passwords.hash(newPassword), now);
        user.changeStatus(UserStatus.ACTIVE, now);
        users.save(user);
        invitation.consume(now);
        invitations.save(invitation);
        events.publishEvent(new IamEvents.UserStatusChanged(user.getId(), user.getOrganizationId(), UserStatus.ACTIVE, now));
        return new IamResources.InvitationAcceptedResource("Invitation accepted. You can now sign in.");
    }

    @Transactional(readOnly = true)
    public IamResources.UserResource profile(IamPrincipal principal) {
        User user = users.findByIdAndOrganizationId(principal.userId(), principal.organizationId())
                .orElseThrow(() -> notFound("User not found"));
        return userResource(user);
    }

    @Transactional
    public IamResources.UserResource registerUser(IamPrincipal actor, UUID organizationId,
                                                  String email, String password, String fullName, Role role) {
        ensureSameTenant(actor, organizationId);
        Organization organization = lockOrganizationForAdminChange(actor);
        validatePassword(password);
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmailIgnoreCase(normalizedEmail)) throw conflict("Email is already registered");
        Instant now = clock.instant();
        User user = users.save(User.register(UUID.randomUUID(), organization.getId(), normalizedEmail,
                passwords.hash(password), fullName.trim(), role, UserStatus.ACTIVE, now));
        events.publishEvent(new IamEvents.UserRegistered(user.getId(), organizationId, normalizedEmail, role, now));
        if (role != Role.VIEWER) events.publishEvent(new IamEvents.RoleAssigned(user.getId(), organizationId, role, now));
        return userResource(user);
    }

    @Transactional
    public IamResources.UserResource assignRole(IamPrincipal actor, UUID userId, Role role) {
        lockOrganizationForAdminChange(actor);
        User target = userInTenant(userId, actor.organizationId());
        if (target.getRole() == Role.ADMIN && role != Role.ADMIN && target.getStatus() == UserStatus.ACTIVE
                && activeAdminCount(actor.organizationId()) <= 1) {
            throw conflict("The last active administrator cannot be demoted");
        }
        if (target.getRole() != role) {
            target.assignRole(role, clock.instant());
            users.save(target);
            events.publishEvent(new IamEvents.RoleAssigned(target.getId(), target.getOrganizationId(), role, clock.instant()));
        }
        return userResource(target);
    }

    @Transactional
    public IamResources.UserResource changeUserStatus(IamPrincipal actor, UUID userId, UserStatus status) {
        lockOrganizationForAdminChange(actor);
        User target = userInTenant(userId, actor.organizationId());
        if (target.getStatus() == UserStatus.ACTIVE && status == UserStatus.INACTIVE && target.getRole() == Role.ADMIN
                && activeAdminCount(actor.organizationId()) <= 1) {
            throw conflict("The last active administrator cannot be deactivated");
        }
        if (target.getStatus() != status) {
            Instant now = clock.instant();
            target.changeStatus(status, now);
            users.save(target);
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
        User user = users.findByIdAndOrganizationId(principal.userId(), principal.organizationId())
                .orElseThrow(() -> unauthorized("Invalid token"));
        Organization organization = organizations.findById(user.getOrganizationId())
                .orElseThrow(() -> unauthorized("Invalid token"));
        AuthenticationSession session = sessions.findById(principal.sessionId())
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
        Organization organization = organizations.save(Organization.create(organizationId,
                request.companyName().trim(), SubscriptionPlan.FREE, OrganizationStatus.ACTIVE, request.requestId(), now));
        String unusablePassword = tokens.newRefreshToken() + "x";
        User admin = users.save(User.register(UUID.randomUUID(), organizationId, email,
                passwords.hash(unusablePassword), request.contactName().trim(), Role.ADMIN, UserStatus.INACTIVE, now));
        String invitationToken = tokens.newInvitationToken();
        UUID invitationId = UUID.randomUUID();
        Instant expiresAt = now.plusSeconds(86_400);
        invitations.save(InitialAdminInvitation.issue(invitationId, admin.getId(), tokens.hashToken(invitationToken), now, expiresAt));
        events.publishEvent(new IamEvents.OrganizationCreated(organization.getId(), organization.getName(), now));
        events.publishEvent(new IamEvents.UserRegistered(admin.getId(), organizationId, email, Role.ADMIN, now));
        events.publishEvent(new IamEvents.RoleAssigned(admin.getId(), organizationId, Role.ADMIN, now));
        events.publishEvent(new IamEvents.InitialAdminInvitationIssued(invitationId, email, invitationToken, expiresAt));
    }

    public IamResources.UserResource userResource(User user) {
        return new IamResources.UserResource(user.getId(), user.getOrganizationId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.getStatus(), user.getLastLoginAt(), user.getCreatedAt());
    }

    public static IamResources.OrganizationResource organizationResource(Organization organization) {
        return new IamResources.OrganizationResource(organization.getId(), organization.getName(),
                organization.getSubscriptionPlan(), organization.getStatus(), organization.getCreatedAt(), organization.getUpdatedAt());
    }

    private AuthenticationResult authentication(User user, Organization organization,
                                                UUID sessionId, String refreshToken) {
        TokenProvider.IssuedAccessToken access = tokens.issueAccessToken(user.getId(), organization.getId(), user.getRole(), sessionId);
        return new AuthenticationResult(access, refreshToken, userResource(user), organizationResource(organization));
    }

    private User userInTenant(UUID userId, UUID organizationId) {
        return users.findByIdAndOrganizationId(userId, organizationId).orElseThrow(() -> notFound("User not found"));
    }

    private long activeAdminCount(UUID organizationId) {
        return users.countByOrganizationIdAndRoleAndStatus(organizationId, Role.ADMIN, UserStatus.ACTIVE);
    }

    // Bloquea la organización para evitar cambios simultáneos que dejen sin administrador activo.
    private Organization lockOrganizationForAdminChange(IamPrincipal actor) {
        if (actor.role() != Role.ADMIN) throw new AccessDeniedException("An active organization administrator is required");
        Organization organization = organizations.findByIdForUpdate(actor.organizationId())
                .filter(o -> o.getStatus() == OrganizationStatus.ACTIVE)
                .orElseThrow(() -> conflict("Organization is suspended or unavailable"));
        User currentActor = users.findByIdAndOrganizationId(actor.userId(), actor.organizationId())
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

    public record AuthenticationResult(TokenProvider.IssuedAccessToken accessToken, String refreshToken,
                                       IamResources.UserResource user, IamResources.OrganizationResource organization) { }
}
