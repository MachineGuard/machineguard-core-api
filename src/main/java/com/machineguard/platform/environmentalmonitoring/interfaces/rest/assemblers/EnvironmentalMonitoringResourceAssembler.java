package com.machineguard.platform.environmentalmonitoring.interfaces.rest.assemblers;

import com.machineguard.platform.environmentalmonitoring.application.queryservices.EnvironmentalMonitoringQueryService.ZoneSnapshot;
import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import com.machineguard.platform.environmentalmonitoring.domain.services.ZoneConditionService;
import com.machineguard.platform.environmentalmonitoring.interfaces.rest.resources.EnvironmentalMonitoringResources.*;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;

@Component
public class EnvironmentalMonitoringResourceAssembler {
    public MeasurementResource toResource(Measurement m) {
        return new MeasurementResource(m.getId(), m.getOrganizationId(), m.getMonitoringZoneId(), m.getMonitoringPointId(),
                m.getSensorNodeId(), m.getEnvironmentalVariable(), m.getMeasuredValue(), m.getRecordedAt(), m.getReceivedAt());
    }
    public PointResource toResource(MonitoringPoint p) {
        return new PointResource(p.getId(), p.getName(), p.getLocation(), p.getStatus());
    }
    public SensorResource toResource(SensorNode n) {
        return new SensorResource(n.getId(), n.getMonitoringPointId(), n.getDeviceCode(), n.getSamplingIntervalSeconds(),
                n.getStatus(), n.getLastMeasurementAt());
    }
    public ThresholdResource toResource(Threshold t) {
        return new ThresholdResource(t.getId(), t.getEnvironmentalVariable(), t.getMinimumValue(), t.getMaximumValue());
    }
    public ZoneResource toResource(ZoneSnapshot snapshot) {
        var z = snapshot.zone();
        return new ZoneResource(z.getId(), z.getOrganizationId(), z.getFacilityId(), z.getName(), z.getDescription(), z.getStatus(),
                ZoneConditionService.evaluate(snapshot.nodes(), snapshot.thresholds(), snapshot.latestMeasurements()),
                (int) snapshot.nodes().stream().filter(n -> n.getStatus() == SensorNodeStatus.ONLINE).count(),
                (int) snapshot.nodes().stream().filter(n -> n.getStatus() == SensorNodeStatus.OFFLINE).count(),
                (int) snapshot.nodes().stream().filter(n -> n.getStatus() == SensorNodeStatus.INACTIVE).count(),
                snapshot.latestMeasurements().stream().map(Measurement::getRecordedAt).max(OffsetDateTime.timeLineOrder()).orElse(null),
                snapshot.points().stream().map(this::toResource).toList(),
                snapshot.nodes().stream().map(this::toResource).toList(),
                snapshot.thresholds().stream().map(this::toResource).toList(),
                snapshot.latestMeasurements().stream().map(this::toResource).toList());
    }
}
