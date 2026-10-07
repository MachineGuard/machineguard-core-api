package com.machineguard.platform.environmentalmonitoring.domain.commands;

import java.util.UUID;

public record RegisterMonitoringPointCommand(UUID organizationId, UUID monitoringZoneId, String name, String location) {}
