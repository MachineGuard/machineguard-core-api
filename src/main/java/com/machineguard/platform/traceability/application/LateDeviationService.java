package com.machineguard.platform.traceability.application;

import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.LateDeviationDetected;
import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;
import com.machineguard.platform.traceability.domain.model.LateDeviation;
import com.machineguard.platform.traceability.domain.repositories.LateDeviationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class LateDeviationService {
    private final LateDeviationRepository records;
    public LateDeviationService(LateDeviationRepository records){this.records=records;}

    @EventListener
    @Transactional
    public void on(LateDeviationDetected event) {
        if (records.existsById(event.measurementId())) return;
        records.saveAndFlush(LateDeviation.record(event.measurementId(),event.organizationId(),event.monitoringZoneId(),
                event.monitoringPointId(),event.sensorNodeId(),EnvironmentalVariable.valueOf(event.environmentalVariable().name()),
                event.measuredValue(),event.minimumValue(),event.maximumValue(),
                event.recordedAt().toInstant(),event.receivedAt().toInstant()));
    }

    @Transactional(readOnly=true)
    public List<LateDeviation> find(UUID organizationId,UUID zoneId,int limit) {
        if (limit<1 || limit>500) throw new IllegalArgumentException("limit must be between 1 and 500");
        return records.findByOrganizationIdAndMonitoringZoneIdOrderByRecordedAtDescMeasurementIdAsc(
                organizationId,zoneId,PageRequest.of(0,limit));
    }
}
