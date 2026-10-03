package com.machineguard.platform.traceability.domain.commands;

import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;

import java.time.Instant;

public record StartExcursionCommand(Long organizationId, Long monitoringZoneId, Long monitoringPointId,
                                    EnvironmentalVariable environmentalVariable, double detectedValue,
                                    double thresholdValue, Instant detectedAt) {
}
