package com.machineguard.platform.environmentalmonitoring.application.queryservices;

import com.machineguard.platform.environmentalmonitoring.domain.queries.EnvironmentalMonitoringQueries.*;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.*;
import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class EnvironmentalMonitoringQueryService {
    private final MeasurementRepository measurements;
    private final MonitoringZoneRepository zones;
    private final MonitoringPointRepository points;
    private final SensorNodeRepository nodes;
    private final ThresholdRepository thresholds;
    public EnvironmentalMonitoringQueryService(MeasurementRepository measurements, MonitoringZoneRepository zones,
            MonitoringPointRepository points, SensorNodeRepository nodes, ThresholdRepository thresholds) {
        this.measurements = measurements;
        this.zones = zones;
        this.points = points;
        this.nodes = nodes;
        this.thresholds = thresholds;
    }
    public Measurement handle(GetMeasurement query) {
        return measurements.findByIdAndOrganizationId(query.measurementId(), query.organizationId())
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Measurement not found"));
    }
    public List<Measurement> handle(GetLatestMeasurements query) {
        if (query.monitoringZoneId() != null) zone(query.organizationId(), query.monitoringZoneId());
        return measurements.findLatest(query.organizationId(), query.monitoringZoneId());
    }
    public MeasurementHistoryPage handle(GetMeasurementHistory query) {
        zone(query.organizationId(), query.monitoringZoneId());
        if (query.from() == null || query.to() == null || query.from().isAfter(query.to())
                || query.page() < 0 || query.size() < 1 || query.size() > 500)
            throw new EnvironmentalMonitoringRuleException("Invalid period or pagination (size: 1..500)");
        if (query.monitoringPointId() != null) points.findByIdAndMonitoringZoneId(query.monitoringPointId(), query.monitoringZoneId())
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Monitoring point not found in zone"));
        return measurements.findHistory(query);
    }
    public List<ZoneSnapshot> handle(GetZones query) {
        return zones.findByOrganizationIdOrderByNameAsc(query.organizationId()).stream().map(this::snapshot).toList();
    }
    public ZoneSnapshot handle(GetZone query) { return snapshot(zone(query.organizationId(), query.monitoringZoneId())); }
    private MonitoringZone zone(UUID organizationId, UUID zoneId) {
        return zones.findByIdAndOrganizationId(zoneId, organizationId)
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Monitoring zone not found"));
    }
    private ZoneSnapshot snapshot(MonitoringZone zone) {
        var zonePoints = points.findByMonitoringZoneIdOrderByNameAsc(zone.getId());
        var zoneNodes = zonePoints.isEmpty() ? List.<SensorNode>of() : nodes.findByMonitoringPointIdIn(zonePoints.stream().map(MonitoringPoint::getId).toList());
        return new ZoneSnapshot(zone, zonePoints, zoneNodes, thresholds.findByMonitoringZoneId(zone.getId()),
                measurements.findLatest(zone.getOrganizationId(), zone.getId()));
    }
    public record ZoneSnapshot(MonitoringZone zone, List<MonitoringPoint> points, List<SensorNode> nodes,
            List<Threshold> thresholds, List<Measurement> latestMeasurements) {}
}
