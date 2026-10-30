package com.machineguard.platform.environmentalmonitoring.application.ports;

import java.util.Optional;
import java.util.UUID;

/** Resolves a physical device code within its authenticated organization. */
public interface GatewayDeviceResolver {
    Optional<Binding> resolve(UUID organizationId, String deviceCode);
    record Binding(UUID monitoringZoneId, UUID monitoringPointId, UUID sensorNodeId) { }
}
