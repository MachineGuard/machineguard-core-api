package com.machineguard.platform.iam.infrastructure.persistence.adapter;

import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.domain.model.User;
import com.machineguard.platform.iam.domain.model.UserStatus;
import com.machineguard.platform.iam.domain.port.UserRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaUserRepositoryAdapter implements UserRepository {
    private final UserJpaRepository repository;

    public JpaUserRepositoryAdapter(UserJpaRepository repository) { this.repository = repository; }

    @Override public Optional<User> findById(UUID id) { return repository.findById(id).map(IamEntityMapper::toDomain); }
    @Override public Optional<User> findByEmailIgnoreCase(String email) { return repository.findByEmailIgnoreCase(email).map(IamEntityMapper::toDomain); }
    @Override public Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId) {
        return repository.findByIdAndOrganizationId(id, organizationId).map(IamEntityMapper::toDomain);
    }
    @Override public boolean existsByEmailIgnoreCase(String email) { return repository.existsByEmailIgnoreCase(email); }
    @Override public boolean existsAny() { return repository.count() > 0; }
    @Override public long countByOrganizationIdAndRoleAndStatus(UUID organizationId, Role role, UserStatus status) {
        return repository.countByOrganizationIdAndRoleAndStatus(organizationId, role, status);
    }
    @Override public User save(User user) { return IamEntityMapper.toDomain(repository.save(IamEntityMapper.toEntity(user))); }
}
