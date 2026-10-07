package com.machineguard.platform.environmentalmonitoring.domain.commands;

import java.util.UUID;

public record RegisterMonitoringZoneCommand(UUID organizationId, UUID facilityId, String name, String description) {}
