package com.machineguard.platform.traceability.application.eventhandlers;

import com.machineguard.platform.traceability.application.commandservices.StartExcursionCommandService;
import com.machineguard.platform.traceability.application.integration.InboundEvents.DeviationDetected;
import com.machineguard.platform.traceability.domain.commands.StartExcursionCommand;
import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.services.ExcursionLifecycleService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class DeviationDetectedEventHandler {

    private final ExcursionLifecycleService lifecycle;
    private final StartExcursionCommandService startExcursion;

    public DeviationDetectedEventHandler(ExcursionLifecycleService lifecycle, StartExcursionCommandService startExcursion) {
        this.lifecycle = lifecycle;
        this.startExcursion = startExcursion;
    }

    /** Sin excursion abierta para la zona y variable: la inicia; con una abierta: solo actualiza el pico. */
    @EventListener
    @Transactional
    public void on(DeviationDetected event) {
        Optional<Excursion> open = lifecycle.findOpen(
                event.organizationId(), event.monitoringZoneId(), event.environmentalVariable());
        if (open.isPresent()) {
            lifecycle.registerDeviation(open.get(), event.measuredValue(), event.detectedAt());
            return;
        }
        startExcursion.handle(new StartExcursionCommand(event.organizationId(), event.monitoringZoneId(),
                event.monitoringPointId(), event.environmentalVariable(), event.measuredValue(),
                event.thresholdValue(), event.detectedAt()));
    }
}
