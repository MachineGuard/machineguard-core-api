package com.machineguard.platform.traceability.application.commandservices;

import com.machineguard.platform.traceability.domain.commands.ChangeDispositionCommand;
import com.machineguard.platform.traceability.domain.commands.RegisterNonConformityCommand;
import com.machineguard.platform.traceability.domain.exceptions.ResourceNotFoundException;
import com.machineguard.platform.traceability.domain.model.Batch;
import com.machineguard.platform.traceability.domain.model.NonConformity;
import com.machineguard.platform.traceability.domain.repositories.ExcursionRepository;
import com.machineguard.platform.traceability.domain.repositories.NonConformityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class RegisterNonConformityCommandService {

    private final ExcursionRepository excursions;
    private final NonConformityRepository nonConformities;
    private final Clock clock;

    public RegisterNonConformityCommandService(ExcursionRepository excursions, NonConformityRepository nonConformities,
                                               Clock clock) {
        this.excursions = excursions;
        this.nonConformities = nonConformities;
        this.clock = clock;
    }

    /** Toda no conformidad debe asociarse a una excursion previamente registrada de la misma organizacion. */
    @Transactional
    public NonConformity handle(RegisterNonConformityCommand command) {
        excursions.findByIdAndOrganizationId(command.excursionId(), command.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Excursion " + command.excursionId() + " no encontrada"));
        Batch batch = new Batch(command.batchCode(), command.productName(), command.quantity(), command.storedAt());
        return nonConformities.save(NonConformity.register(command.excursionId(), command.organizationId(), batch,
                command.classification(), command.registeredBy(), clock.instant()));
    }

    @Transactional
    public NonConformity handle(ChangeDispositionCommand command) {
        NonConformity nc = nonConformities.findByIdAndOrganizationId(command.nonConformityId(), command.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No conformidad " + command.nonConformityId() + " no encontrada"));
        switch (command.disposition()) {
            case RELEASED -> nc.release(command.justification(), clock.instant());
            case DISCARDED -> nc.discard(command.justification(), clock.instant());
            case QUARANTINED -> nc.quarantine();
        }
        return nonConformities.save(nc);
    }
}
