package com.machineguard.platform.environmentalmonitoring.domain.commands;

import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;
import java.math.BigDecimal;
import java.util.UUID;

public record ConfigureThresholdCommand(UUID organizationId, UUID monitoringZoneId, EnvironmentalVariable environmentalVariable,
        BigDecimal minimumValue, BigDecimal maximumValue) {}
