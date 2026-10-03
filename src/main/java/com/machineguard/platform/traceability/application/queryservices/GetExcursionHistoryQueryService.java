package com.machineguard.platform.traceability.application.queryservices;

import com.machineguard.platform.traceability.domain.exceptions.ResourceNotFoundException;
import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.model.ExcursionStatus;
import com.machineguard.platform.traceability.domain.model.NonConformity;
import com.machineguard.platform.traceability.domain.queries.GetExcursionHistoryQuery;
import com.machineguard.platform.traceability.domain.repositories.ExcursionRepository;
import com.machineguard.platform.traceability.domain.repositories.NonConformityRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetExcursionHistoryQueryService {

    private final ExcursionRepository excursions;
    private final NonConformityRepository nonConformities;

    public GetExcursionHistoryQueryService(ExcursionRepository excursions, NonConformityRepository nonConformities) {
        this.excursions = excursions;
        this.nonConformities = nonConformities;
    }

    /** Excursiones que se solapan con [from, to]; las abiertas no tienen fin y se consideran vigentes. */
    public List<Excursion> handle(GetExcursionHistoryQuery query) {
        Specification<Excursion> spec = (root, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), query.organizationId()));
            if (query.monitoringZoneId() != null) {
                p.add(cb.equal(root.get("monitoringZoneId"), query.monitoringZoneId()));
            }
            if (query.status() != null) {
                p.add(cb.equal(root.get("status"), query.status()));
            }
            if (query.to() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("startedAt"), query.to()));
            }
            if (query.from() != null) {
                p.add(cb.or(cb.isNull(root.get("endedAt")), cb.greaterThanOrEqualTo(root.get("endedAt"), query.from())));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return excursions.findAll(spec, Sort.by(Sort.Direction.DESC, "startedAt"));
    }

    public List<Excursion> findOpen(UUID organizationId) {
        return excursions.findByOrganizationIdAndStatusOrderByStartedAtDesc(organizationId, ExcursionStatus.ONGOING);
    }

    public Excursion getById(UUID organizationId, UUID excursionId) {
        return excursions.findByIdAndOrganizationId(excursionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Excursion " + excursionId + " no encontrada"));
    }

    public List<NonConformity> nonConformitiesOf(UUID organizationId, UUID excursionId) {
        getById(organizationId, excursionId);
        return nonConformities.findByExcursionIdAndOrganizationIdOrderByRegisteredAtAsc(excursionId, organizationId);
    }
}
