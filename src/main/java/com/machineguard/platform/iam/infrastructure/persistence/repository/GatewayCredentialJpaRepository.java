package com.machineguard.platform.iam.infrastructure.persistence.repository;

import com.machineguard.platform.iam.infrastructure.persistence.GatewayCredentialEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatewayCredentialJpaRepository extends JpaRepository<GatewayCredentialEntity, UUID> {
    Optional<GatewayCredentialEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<GatewayCredentialEntity> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GatewayCredentialEntity g where g.id=:id and g.organizationId=:organizationId")
    Optional<GatewayCredentialEntity> lockByIdAndOrganizationId(UUID id, UUID organizationId);
}
