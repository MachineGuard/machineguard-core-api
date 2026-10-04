package com.machineguard.platform.iam.infrastructure.persistence.adapter;

import com.machineguard.platform.iam.domain.model.InitialAdminInvitation;
import com.machineguard.platform.iam.domain.port.InitialAdminInvitationRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.InitialAdminInvitationJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaInitialAdminInvitationRepositoryAdapter implements InitialAdminInvitationRepository {
    private final InitialAdminInvitationJpaRepository repository;

    public JpaInitialAdminInvitationRepositoryAdapter(InitialAdminInvitationJpaRepository repository) {
        this.repository = repository;
    }

    @Override public Optional<InitialAdminInvitation> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(IamEntityMapper::toDomain);
    }
    @Override public InitialAdminInvitation save(InitialAdminInvitation invitation) {
        return IamEntityMapper.toDomain(repository.save(IamEntityMapper.toEntity(invitation)));
    }
}
