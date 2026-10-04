package com.machineguard.platform.iam.infrastructure.persistence.adapter;

import com.machineguard.platform.iam.domain.model.BootstrapState;
import com.machineguard.platform.iam.domain.port.BootstrapStateRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.BootstrapStateJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaBootstrapStateRepositoryAdapter implements BootstrapStateRepository {
    private final BootstrapStateJpaRepository repository;

    public JpaBootstrapStateRepositoryAdapter(BootstrapStateJpaRepository repository) { this.repository = repository; }

    @Override public Optional<BootstrapState> findByKey(String key) {
        return repository.findById(key).map(IamEntityMapper::toDomain);
    }
    @Override public BootstrapState save(BootstrapState state) {
        return IamEntityMapper.toDomain(repository.save(IamEntityMapper.toEntity(state)));
    }
}
