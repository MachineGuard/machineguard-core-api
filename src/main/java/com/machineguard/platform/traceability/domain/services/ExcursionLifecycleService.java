package com.machineguard.platform.traceability.domain.services;

import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;
import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.model.ExcursionStatus;
import com.machineguard.platform.traceability.domain.repositories.ExcursionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Decide si una desviacion inicia una excursion nueva o prolonga la que ya esta abierta. */
@Component
public class ExcursionLifecycleService {

    private final ExcursionRepository excursions;

    public ExcursionLifecycleService(ExcursionRepository excursions) {
        this.excursions = excursions;
    }

    public Optional<Excursion> findOpen(Long organizationId, Long zoneId, EnvironmentalVariable variable) {
        return excursions.findByOrganizationIdAndMonitoringZoneIdAndEnvironmentalVariableAndStatus(
                organizationId, zoneId, variable, ExcursionStatus.ONGOING);
    }

    public List<Excursion> findOpenInZone(Long organizationId, Long zoneId, EnvironmentalVariable variableOrNull) {
        if (variableOrNull != null) {
            return findOpen(organizationId, zoneId, variableOrNull).stream().toList();
        }
        return excursions.findByOrganizationIdAndMonitoringZoneIdAndStatus(organizationId, zoneId, ExcursionStatus.ONGOING);
    }

    public Excursion registerDeviation(Excursion open, double value, Instant at) {
        open.registerDeviation(value, at);
        return excursions.save(open);
    }
}
