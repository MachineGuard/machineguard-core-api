package com.machineguard.platform.traceability.application.eventhandlers;

import com.machineguard.platform.traceability.application.commandservices.EndExcursionCommandService;
import com.machineguard.platform.traceability.application.integration.InboundEvents.IncidentResolved;
import com.machineguard.platform.traceability.application.integration.InboundEvents.SafeRangeRestored;
import com.machineguard.platform.traceability.domain.commands.EndExcursionCommand;
import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.model.ExcursionStatus;
import com.machineguard.platform.traceability.domain.repositories.ExcursionRepository;
import com.machineguard.platform.traceability.domain.services.ExcursionLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cierra y completa la cadena de evidencia: vincula el incidente resuelto con su excursion
 * y cierra la excursion cuando las condiciones vuelven al Safe Range.
 */
@Component
public class IncidentResolvedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(IncidentResolvedEventHandler.class);

    private final ExcursionRepository excursions;
    private final ExcursionLifecycleService lifecycle;
    private final EndExcursionCommandService endExcursion;

    public IncidentResolvedEventHandler(ExcursionRepository excursions, ExcursionLifecycleService lifecycle,
                                        EndExcursionCommandService endExcursion) {
        this.excursions = excursions;
        this.lifecycle = lifecycle;
        this.endExcursion = endExcursion;
    }

    @EventListener
    @Transactional
    public void on(IncidentResolved event) {
        if (excursions.findFirstByOrganizationIdAndIncidentId(event.organizationId(), event.incidentId()).isPresent()) {
            return; // evento repetido: el incidente ya esta vinculado
        }
        List<Excursion> targets = event.openedAt() == null
                ? lifecycle.findOpenInZone(event.organizationId(), event.monitoringZoneId(), event.environmentalVariable())
                : excursions.findUnlinkedInIncidentPeriod(event.organizationId(), event.monitoringZoneId(),
                        event.environmentalVariable(), event.openedAt(), event.resolvedAt());
        if (targets.isEmpty() && event.openedAt() == null) {
            // El Safe Range pudo restablecerse antes: se vincula a la excursion cerrada mas reciente sin incidente.
            targets = excursions
                    .findByOrganizationIdAndMonitoringZoneIdAndStatusAndIncidentIdIsNullOrderByEndedAtDesc(
                            event.organizationId(), event.monitoringZoneId(), ExcursionStatus.CLOSED)
                    .stream()
                    .filter(e -> event.environmentalVariable() == null
                            || e.getEnvironmentalVariable() == event.environmentalVariable())
                    .limit(1)
                    .toList();
        }
        if (targets.isEmpty()) {
            log.warn("IncidentResolved {} sin excursion asociada en la zona {}", event.incidentId(), event.monitoringZoneId());
            return;
        }
        for (Excursion excursion : targets) {
            excursion.linkIncident(event.incidentId(), event.correctiveAction());
            excursions.save(excursion);
            if (excursion.isOngoing()) {
                endExcursion.handle(new EndExcursionCommand(event.organizationId(), excursion.getId(), event.resolvedAt()));
            }
        }
    }

    @EventListener
    @Transactional
    public void on(SafeRangeRestored event) {
        for (Excursion excursion : lifecycle.findOpenInZone(
                event.organizationId(), event.monitoringZoneId(), event.environmentalVariable())) {
            endExcursion.handle(new EndExcursionCommand(event.organizationId(), excursion.getId(), event.restoredAt()));
        }
    }
}
