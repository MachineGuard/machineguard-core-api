package com.machineguard.platform.iam.domain.port;

import com.machineguard.platform.iam.domain.model.InitialAdminInvitation;

import java.util.Optional;

public interface InitialAdminInvitationRepository {
    Optional<InitialAdminInvitation> findByTokenHash(String tokenHash);
    InitialAdminInvitation save(InitialAdminInvitation invitation);
}
