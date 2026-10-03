package com.machineguard.platform.traceability.domain.commands;

import java.time.Instant;

public record EndExcursionCommand(Long organizationId, Long excursionId, Instant endedAt) {
}
