package com.machineguard.platform.traceability.domain.commands;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RegisterNonConformityCommand(UUID organizationId, UUID excursionId, String batchCode,
                                           String productName, BigDecimal quantity, Instant storedAt,
                                           String classification, UUID registeredBy) {
}
