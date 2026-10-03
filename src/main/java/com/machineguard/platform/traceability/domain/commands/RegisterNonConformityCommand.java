package com.machineguard.platform.traceability.domain.commands;

import java.math.BigDecimal;
import java.time.Instant;

public record RegisterNonConformityCommand(Long organizationId, Long excursionId, String batchCode,
                                           String productName, BigDecimal quantity, Instant storedAt,
                                           String classification, Long registeredBy) {
}
