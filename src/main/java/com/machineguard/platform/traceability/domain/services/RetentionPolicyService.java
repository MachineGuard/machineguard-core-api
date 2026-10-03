package com.machineguard.platform.traceability.domain.services;

import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.model.RetentionPeriod;
import com.machineguard.platform.traceability.domain.repositories.ExcursionRepository;
import com.machineguard.platform.traceability.domain.repositories.NonConformityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aplica el Retention Period: depura las excursiones cerradas vencidas, salvo las que forman parte de un
 * reporte sellado (la evidencia sellada no se depura aunque haya vencido).
 */
@Component
public class RetentionPolicyService {

    private static final Logger log = LoggerFactory.getLogger(RetentionPolicyService.class);

    private final ExcursionRepository excursions;
    private final NonConformityRepository nonConformities;
    private final Clock clock;

    public RetentionPolicyService(ExcursionRepository excursions, NonConformityRepository nonConformities, Clock clock) {
        this.excursions = excursions;
        this.nonConformities = nonConformities;
        this.clock = clock;
    }

    public List<Excursion> findPurgeable(RetentionPeriod retention) {
        Instant now = clock.instant();
        Instant cutoff = now.atZone(java.time.ZoneOffset.UTC).minusMonths(retention.months()).toInstant();
        return excursions.findPurgeable(cutoff).stream()
                .filter(e -> retention.isExpired(e.getEndedAt(), now))
                .toList();
    }

    @Transactional
    public int purgeExpired(RetentionPeriod retention) {
        List<Excursion> expired = findPurgeable(retention);
        if (expired.isEmpty()) {
            return 0;
        }
        List<UUID> ids = expired.stream().map(Excursion::getId).toList();
        nonConformities.deleteByExcursionIdIn(ids);
        excursions.deleteAll(expired);
        log.info("Retention Period de {} meses: se depuraron {} excursiones", retention.months(), ids.size());
        return ids.size();
    }
}
