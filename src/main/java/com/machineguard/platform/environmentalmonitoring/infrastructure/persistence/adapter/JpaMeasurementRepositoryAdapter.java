package com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.adapter;

import com.machineguard.platform.environmentalmonitoring.domain.model.Measurement;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.MeasurementRepository;
import com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.repository.MeasurementJpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;
import com.machineguard.platform.environmentalmonitoring.domain.model.MeasurementHistoryPage;
import com.machineguard.platform.environmentalmonitoring.domain.queries.EnvironmentalMonitoringQueries.GetMeasurementHistory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.OffsetDateTime;
import java.util.ArrayList;

@Repository
public class JpaMeasurementRepositoryAdapter implements MeasurementRepository {
    private final MeasurementJpaRepository repository;

    public JpaMeasurementRepositoryAdapter(MeasurementJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Measurement save(Measurement measurement) { return repository.saveAndFlush(measurement); }
    @Override
    public boolean existsByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableAndRecordedAt(
            UUID organizationId, UUID monitoringPointId, EnvironmentalVariable environmentalVariable, OffsetDateTime recordedAt) {
        return repository.existsByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableAndRecordedAt(
                organizationId, monitoringPointId, environmentalVariable, recordedAt);
    }
    @Override
    public Optional<Measurement> findByIdAndOrganizationId(UUID id, UUID organizationId) {
        return repository.findByIdAndOrganizationId(id, organizationId);
    }
    @Override
    public Optional<Measurement> findByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableAndRecordedAt(
            UUID organizationId, UUID monitoringPointId, EnvironmentalVariable variable, OffsetDateTime recordedAt) {
        return repository.findByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableAndRecordedAt(
                organizationId,monitoringPointId,variable,recordedAt);
    }
    @Override
    public Optional<Measurement> findFirstByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableOrderByRecordedAtDesc(
            UUID organizationId, UUID monitoringPointId, EnvironmentalVariable environmentalVariable) {
        return repository.findFirstByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableOrderByRecordedAtDesc(
                organizationId, monitoringPointId, environmentalVariable);
    }
    @Override
    public List<Measurement> findLatest(UUID organizationId, UUID zoneId) {
        return repository.findLatest(organizationId, zoneId);
    }
    @Override public List<Measurement> findLatestSensorMeasurements(UUID organizationId,UUID zoneId) {
        return repository.findLatestSensorMeasurements(organizationId,zoneId);
    }
    @Override public List<Measurement> findLatestSensorReceipts(UUID organizationId,UUID zoneId) {
        return repository.findLatestSensorReceipts(organizationId,zoneId);
    }
    @Override
    public MeasurementHistoryPage findHistory(GetMeasurementHistory query) {
        Specification<Measurement> filter = (root, criteria, builder) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.equal(root.get("organizationId"), query.organizationId()));
            predicates.add(builder.equal(root.get("monitoringZoneId"), query.monitoringZoneId()));
            predicates.add(builder.between(root.get("recordedAt"), query.from(), query.to()));
            if (query.monitoringPointId() != null) predicates.add(builder.equal(root.get("monitoringPointId"), query.monitoringPointId()));
            if (query.environmentalVariable() != null) predicates.add(builder.equal(root.get("environmentalVariable"), query.environmentalVariable()));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var page = repository.findAll(filter, PageRequest.of(query.page(), query.size(), Sort.by("recordedAt", "id")));
        return new MeasurementHistoryPage(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
