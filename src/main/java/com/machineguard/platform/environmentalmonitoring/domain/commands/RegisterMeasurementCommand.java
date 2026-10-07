package com.machineguard.platform.environmentalmonitoring.domain.commands;

import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RegisterMeasurementCommand(UUID organizationId, UUID monitoringZoneId, UUID monitoringPointId,
        UUID sensorNodeId, EnvironmentalVariable environmentalVariable, BigDecimal measuredValue, OffsetDateTime recordedAt) {}
