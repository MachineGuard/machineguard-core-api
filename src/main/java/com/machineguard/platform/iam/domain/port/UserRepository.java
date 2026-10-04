package com.machineguard.platform.iam.domain.port;

import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.domain.model.User;
import com.machineguard.platform.iam.domain.model.UserStatus;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(UUID id);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsAny();
    long countByOrganizationIdAndRoleAndStatus(UUID organizationId, Role role, UserStatus status);
    User save(User user);
}
