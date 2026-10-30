package com.machineguard.platform.iam.application.port;
import java.util.UUID;

/** Logical ownership check supplied by Environmental Monitoring integration. */
public interface GatewaySensorOwnership {
    boolean belongsToOrganization(UUID sensorNodeId, UUID organizationId);
}
