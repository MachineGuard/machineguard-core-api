package com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.repository;

import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface MeasurementJpaRepository extends JpaRepository<Measurement, UUID>, JpaSpecificationExecutor<Measurement> {
    boolean existsByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableAndRecordedAt(UUID organizationId, UUID monitoringPointId, EnvironmentalVariable environmentalVariable, java.time.OffsetDateTime recordedAt);
    Optional<Measurement> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<Measurement> findByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableAndRecordedAt(
            UUID organizationId, UUID monitoringPointId, EnvironmentalVariable environmentalVariable, java.time.OffsetDateTime recordedAt);
    Optional<Measurement> findFirstByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableOrderByRecordedAtDesc(UUID organizationId, UUID monitoringPointId, EnvironmentalVariable environmentalVariable);
    @Query("""
            select m from Measurement m where m.organizationId = :organizationId
            and (:zoneId is null or m.monitoringZoneId = :zoneId)
            and not exists (select n.id from Measurement n where n.organizationId = m.organizationId
                and n.monitoringPointId = m.monitoringPointId and n.environmentalVariable = m.environmentalVariable
                and n.recordedAt > m.recordedAt)
            order by m.monitoringPointId, m.environmentalVariable
            """)
    List<Measurement> findLatest(UUID organizationId, UUID zoneId);
    @Query("""
            select m from Measurement m where m.organizationId=:organizationId and m.monitoringZoneId=:zoneId
            and m.source=com.machineguard.platform.environmentalmonitoring.domain.model.MeasurementSource.SENSOR
            and not exists (select n.id from Measurement n where n.organizationId=m.organizationId
                and n.monitoringZoneId=m.monitoringZoneId
                and n.source=com.machineguard.platform.environmentalmonitoring.domain.model.MeasurementSource.SENSOR
                and n.sensorNodeId=m.sensorNodeId and n.environmentalVariable=m.environmentalVariable and n.recordedAt>m.recordedAt)
            """)
    List<Measurement> findLatestSensorMeasurements(UUID organizationId,UUID zoneId);
    @Query("""
            select m from Measurement m where m.organizationId=:organizationId and m.monitoringZoneId=:zoneId
            and m.source=com.machineguard.platform.environmentalmonitoring.domain.model.MeasurementSource.SENSOR
            and not exists (select n.id from Measurement n where n.organizationId=m.organizationId
                and n.monitoringZoneId=m.monitoringZoneId
                and n.source=com.machineguard.platform.environmentalmonitoring.domain.model.MeasurementSource.SENSOR
                and n.sensorNodeId=m.sensorNodeId and n.receivedAt>m.receivedAt)
            """)
    List<Measurement> findLatestSensorReceipts(UUID organizationId,UUID zoneId);
}
