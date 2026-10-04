package com.machineguard.platform.iam.infrastructure.persistence.adapter;

import com.machineguard.platform.iam.domain.model.UsedRefreshToken;
import com.machineguard.platform.iam.domain.port.UsedRefreshTokenRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.UsedRefreshTokenJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaUsedRefreshTokenRepositoryAdapter implements UsedRefreshTokenRepository {
    private final UsedRefreshTokenJpaRepository repository;

    public JpaUsedRefreshTokenRepositoryAdapter(UsedRefreshTokenJpaRepository repository) { this.repository = repository; }

    @Override public Optional<UsedRefreshToken> findByTokenHash(String tokenHash) {
        return repository.findById(tokenHash).map(IamEntityMapper::toDomain);
    }
    @Override public UsedRefreshToken save(UsedRefreshToken token) {
        return IamEntityMapper.toDomain(repository.save(IamEntityMapper.toEntity(token)));
    }
}
