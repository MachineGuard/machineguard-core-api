package com.machineguard.platform.iam.infrastructure.security;
import java.util.Set;
import java.util.UUID;

public record GatewayPrincipal(UUID gatewayId, UUID organizationId, Set<UUID> sensorNodeIds) {
    public GatewayPrincipal { sensorNodeIds=Set.copyOf(sensorNodeIds); }
}
