package com.machineguard.platform.iam.infrastructure.persistence.adapter;

import com.machineguard.platform.iam.domain.model.Gateway;
import com.machineguard.platform.iam.domain.port.GatewayRepository;
import com.machineguard.platform.iam.infrastructure.persistence.GatewayCredentialEntity;
import com.machineguard.platform.iam.infrastructure.persistence.repository.GatewayCredentialJpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class JpaGatewayRepositoryAdapter implements GatewayRepository {
    private final GatewayCredentialJpaRepository repository;
    public JpaGatewayRepositoryAdapter(GatewayCredentialJpaRepository repository) { this.repository=repository; }
    public Gateway save(Gateway gateway) { return repository.saveAndFlush(new GatewayCredentialEntity(gateway)).toDomain(); }
    public Optional<Gateway> findById(UUID id) { return repository.findById(id).map(GatewayCredentialEntity::toDomain); }
    public Optional<Gateway> findByIdAndOrganizationId(UUID id, UUID org) {
        return repository.findByIdAndOrganizationId(id,org).map(GatewayCredentialEntity::toDomain);
    }
    public Optional<Gateway> lockByIdAndOrganizationId(UUID id, UUID org) {
        return repository.lockByIdAndOrganizationId(id,org).map(GatewayCredentialEntity::toDomain);
    }
    public List<Gateway> findByOrganizationId(UUID org) {
        return repository.findByOrganizationIdOrderByCreatedAtAsc(org).stream().map(GatewayCredentialEntity::toDomain).toList();
    }
}
