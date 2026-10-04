package com.machineguard.platform.iam.infrastructure.persistence.adapter;

import com.machineguard.platform.iam.domain.model.AuthenticationSession;
import com.machineguard.platform.iam.domain.model.BootstrapState;
import com.machineguard.platform.iam.domain.model.InitialAdminInvitation;
import com.machineguard.platform.iam.domain.model.Organization;
import com.machineguard.platform.iam.domain.model.UsedRefreshToken;
import com.machineguard.platform.iam.domain.model.User;
import com.machineguard.platform.iam.infrastructure.persistence.AuthenticationSessionEntity;
import com.machineguard.platform.iam.infrastructure.persistence.BootstrapStateEntity;
import com.machineguard.platform.iam.infrastructure.persistence.InitialAdminInvitationEntity;
import com.machineguard.platform.iam.infrastructure.persistence.OrganizationEntity;
import com.machineguard.platform.iam.infrastructure.persistence.UsedRefreshTokenEntity;
import com.machineguard.platform.iam.infrastructure.persistence.UserEntity;

final class IamEntityMapper {
    private IamEntityMapper() { }

    static Organization toDomain(OrganizationEntity entity) {
        return Organization.restore(entity.getId(), entity.getName(), entity.getSubscriptionPlan(), entity.getStatus(),
                entity.getAcquisitionRequestId(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    static OrganizationEntity toEntity(Organization organization) {
        return new OrganizationEntity(organization.getId(), organization.getName(), organization.getSubscriptionPlan(),
                organization.getStatus(), organization.getAcquisitionRequestId(), organization.getCreatedAt(),
                organization.getUpdatedAt());
    }

    static User toDomain(UserEntity entity) {
        return User.restore(entity.getId(), entity.getOrganizationId(), entity.getEmail(), entity.getPasswordHash(),
                entity.getFullName(), entity.getRole(), entity.getStatus(), entity.getLastLoginAt(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    static UserEntity toEntity(User user) {
        return new UserEntity(user.getId(), user.getOrganizationId(), user.getEmail(), user.getPasswordHash(),
                user.getFullName(), user.getRole(), user.getStatus(), user.getLastLoginAt(), user.getCreatedAt(),
                user.getUpdatedAt());
    }

    static AuthenticationSession toDomain(AuthenticationSessionEntity entity) {
        return AuthenticationSession.restore(entity.getId(), entity.getUserId(), entity.getRefreshTokenHash(),
                entity.getIssuedAt(), entity.getExpiresAt(), entity.getRevokedAt());
    }

    static AuthenticationSessionEntity toEntity(AuthenticationSession session) {
        return new AuthenticationSessionEntity(session.getId(), session.getUserId(), session.getRefreshTokenHash(),
                session.getIssuedAt(), session.getExpiresAt(), session.getRevokedAt());
    }

    static InitialAdminInvitation toDomain(InitialAdminInvitationEntity entity) {
        return InitialAdminInvitation.restore(entity.getId(), entity.getUserId(), entity.getTokenHash(),
                entity.getIssuedAt(), entity.getExpiresAt(), entity.getConsumedAt());
    }

    static InitialAdminInvitationEntity toEntity(InitialAdminInvitation invitation) {
        return new InitialAdminInvitationEntity(invitation.getId(), invitation.getUserId(), invitation.getTokenHash(),
                invitation.getIssuedAt(), invitation.getExpiresAt(), invitation.getConsumedAt());
    }

    static UsedRefreshToken toDomain(UsedRefreshTokenEntity entity) {
        return new UsedRefreshToken(entity.getTokenHash(), entity.getSessionId(), entity.getUsedAt());
    }

    static UsedRefreshTokenEntity toEntity(UsedRefreshToken token) {
        return new UsedRefreshTokenEntity(token.tokenHash(), token.sessionId(), token.usedAt());
    }

    static BootstrapState toDomain(BootstrapStateEntity entity) {
        return new BootstrapState(entity.getBootstrapKey(), entity.getCompletedAt());
    }

    static BootstrapStateEntity toEntity(BootstrapState state) {
        return new BootstrapStateEntity(state.key(), state.completedAt());
    }
}
