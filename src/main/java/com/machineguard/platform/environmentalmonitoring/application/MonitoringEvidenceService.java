package com.machineguard.platform.environmentalmonitoring.application;

import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** Shared read-only assessment for projections and resolution under the producer's zone lock. */
@Service
public class MonitoringEvidenceService {
    private final MonitoringZoneRepository zones;
    private final MonitoringPointRepository points;
    private final SensorNodeRepository nodes;
    private final MeasurementRepository measurements;
    private final ThresholdRepository thresholds;
    private final MeasurementFreshnessPolicy freshness;
    public MonitoringEvidenceService(MonitoringZoneRepository zones,MonitoringPointRepository points,SensorNodeRepository nodes,
            MeasurementRepository measurements,ThresholdRepository thresholds,MeasurementFreshnessPolicy freshness) {
        this.zones=zones;this.points=points;this.nodes=nodes;this.measurements=measurements;this.thresholds=thresholds;this.freshness=freshness;
    }
    public record Assessment(boolean withinSafeRange,List<String> reasons) { }
    public Assessment assess(UUID organization,UUID zoneId,EnvironmentalVariable variable) {
        Set<String> reasons=new LinkedHashSet<>();
        var zone=zones.findByIdAndOrganizationId(zoneId,organization);
        if (zone.isEmpty() || zone.get().getStatus()!=MonitoringZoneStatus.ACTIVE) reasons.add("ZONE_INACTIVE");
        var allPoints=points.findByMonitoringZoneIdOrderByNameAsc(zoneId);
        var allNodes=allPoints.isEmpty()?List.<SensorNode>of():nodes.findByMonitoringPointIdIn(allPoints.stream().map(MonitoringPoint::getId).toList());
        var eligible=allNodes.stream().filter(n->n.getStatus()!=SensorNodeStatus.INACTIVE).toList();
        var required=allPoints.stream().filter(p->p.getStatus()==MonitoringPointStatus.ACTIVE
            && eligible.stream().anyMatch(n->n.getMonitoringPointId().equals(p.getId()))).toList();
        if (required.isEmpty()) reasons.add("NO_REQUIRED_POINTS");
        var threshold=variable==null?Optional.<Threshold>empty():thresholds.findByMonitoringZoneIdAndEnvironmentalVariable(zoneId,variable);
        if (threshold.isEmpty()) reasons.add("MISSING_THRESHOLD");
        var physical=measurements.findLatestSensorMeasurements(organization,zoneId).stream()
                .filter(m->m.getEnvironmentalVariable()==variable).toList();
        var latest=measurements.findLatest(organization,zoneId);
        for (var point:required) {
            var readings=physical.stream().filter(m->m.getMonitoringPointId().equals(point.getId())
                    && eligible.stream().anyMatch(n->n.getId().equals(m.getSensorNodeId()))).toList();
            if (readings.isEmpty()) {
                reasons.add("MISSING_POINT");
                var nonPhysical=latest.stream().filter(m->m.getMonitoringPointId().equals(point.getId()) && m.getEnvironmentalVariable()==variable).toList();
                if (nonPhysical.isEmpty()) reasons.add("NO_DATA");
                else if (nonPhysical.stream().allMatch(m->m.getSource()==MeasurementSource.MANUAL)) reasons.add("MANUAL_ONLY");
                else reasons.add("LEGACY_UNKNOWN");
                continue;
            }
            // A point is covered by one fresh eligible sensor. Choose the most recent capture,
            // then reception and ID; do not let a stale sensor or a newer manual row hide it.
            var current=readings.stream().filter(m->eligible.stream().anyMatch(n->n.getId().equals(m.getSensorNodeId()) && freshness.fresh(m,n)))
                .max(Comparator.comparing((Measurement m)->m.getRecordedAt().toInstant())
                    .thenComparing(m->m.getReceivedAt().toInstant()).thenComparing(Measurement::getId));
            if (current.isEmpty()) reasons.add("STALE");
            else if (threshold.isPresent() && !threshold.get().contains(current.get().getMeasuredValue())) reasons.add("OUT_OF_RANGE");
        }
        return new Assessment(reasons.isEmpty(),List.copyOf(reasons));
    }
}
