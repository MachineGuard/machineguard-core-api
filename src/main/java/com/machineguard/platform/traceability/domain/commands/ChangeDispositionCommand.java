package com.machineguard.platform.traceability.domain.commands;

import com.machineguard.platform.traceability.domain.model.Disposition;
import java.util.UUID;

public record ChangeDispositionCommand(UUID organizationId, UUID nonConformityId, Disposition disposition,
                                       String justification) {
}
