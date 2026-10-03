package com.machineguard.platform.traceability.domain.commands;

import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;

import java.time.Instant;
import java.util.UUID;

public record StartExcursionCommand(UUID organizationId, UUID monitoringZoneId, UUID monitoringPointId,
                                    EnvironmentalVariable environmentalVariable, double detectedValue,
                                    double thresholdValue, Instant detectedAt) {
}
