package com.machineguard.platform.iam.domain.port;

import com.machineguard.platform.iam.domain.model.Gateway;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatewayRepository {
    Gateway save(Gateway gateway);
    Optional<Gateway> findById(UUID id);
    Optional<Gateway> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<Gateway> lockByIdAndOrganizationId(UUID id, UUID organizationId);
    List<Gateway> findByOrganizationId(UUID organizationId);
}
