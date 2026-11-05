package com.machineguard.platform.environmentalmonitoring.domain.repositories;

import com.machineguard.platform.environmentalmonitoring.domain.model.Measurement;
import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;
import com.machineguard.platform.environmentalmonitoring.domain.model.MeasurementHistoryPage;
import com.machineguard.platform.environmentalmonitoring.domain.queries.EnvironmentalMonitoringQueries.GetMeasurementHistory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeasurementRepository {
    /** Persist and flush the reading before its synchronous domain events are published. */
    Measurement save(Measurement measurement);
    boolean existsByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableAndRecordedAt(
            UUID organizationId, UUID monitoringPointId, EnvironmentalVariable environmentalVariable, OffsetDateTime recordedAt);
    Optional<Measurement> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<Measurement> findByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableAndRecordedAt(
            UUID organizationId, UUID monitoringPointId, EnvironmentalVariable variable, OffsetDateTime recordedAt);
    Optional<Measurement> findFirstByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableOrderByRecordedAtDesc(
            UUID organizationId, UUID monitoringPointId, EnvironmentalVariable environmentalVariable);
    List<Measurement> findLatest(UUID organizationId, UUID zoneId);
    List<Measurement> findLatestSensorMeasurements(UUID organizationId,UUID zoneId);
    List<Measurement> findLatestSensorReceipts(UUID organizationId,UUID zoneId);
    MeasurementHistoryPage findHistory(GetMeasurementHistory query);
}
