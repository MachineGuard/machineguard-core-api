package com.machineguard.platform.environmentalmonitoring.domain.commands;

import java.util.UUID;

public record RegisterSensorNodeCommand(UUID organizationId, UUID monitoringZoneId, UUID monitoringPointId,
        String deviceCode, int samplingIntervalSeconds) {}
