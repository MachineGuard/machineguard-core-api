package com.machineguard.platform.iam.infrastructure.persistence.adapter;

import com.machineguard.platform.iam.domain.model.AuthenticationSession;
import com.machineguard.platform.iam.domain.port.AuthenticationSessionRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.AuthenticationSessionJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAuthenticationSessionRepositoryAdapter implements AuthenticationSessionRepository {
    private final AuthenticationSessionJpaRepository repository;

    public JpaAuthenticationSessionRepositoryAdapter(AuthenticationSessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override public Optional<AuthenticationSession> findById(UUID id) {
        return repository.findById(id).map(IamEntityMapper::toDomain);
    }
    @Override public Optional<AuthenticationSession> findByRefreshTokenHash(String tokenHash) {
        return repository.findByRefreshTokenHash(tokenHash).map(IamEntityMapper::toDomain);
    }
    @Override public AuthenticationSession save(AuthenticationSession session) {
        return IamEntityMapper.toDomain(repository.save(IamEntityMapper.toEntity(session)));
    }
    @Override public int revokeAllByUserId(UUID userId, Instant now) {
        return repository.revokeAllByUserId(userId, now);
    }
}
