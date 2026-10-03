package com.machineguard.platform.traceability.domain.commands;

import java.time.Instant;
import java.util.UUID;

public record EndExcursionCommand(UUID organizationId, UUID excursionId, Instant endedAt) {
}
