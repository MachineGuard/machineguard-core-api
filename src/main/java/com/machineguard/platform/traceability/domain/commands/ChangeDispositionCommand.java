package com.machineguard.platform.traceability.domain.commands;

import com.machineguard.platform.traceability.domain.model.Disposition;

public record ChangeDispositionCommand(Long organizationId, Long nonConformityId, Disposition disposition,
                                       String justification) {
}
