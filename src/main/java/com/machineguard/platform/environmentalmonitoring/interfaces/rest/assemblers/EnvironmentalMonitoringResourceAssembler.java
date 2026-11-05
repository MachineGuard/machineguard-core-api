package com.machineguard.platform.environmentalmonitoring.interfaces.rest.assemblers;

import com.machineguard.platform.environmentalmonitoring.application.queryservices.EnvironmentalMonitoringQueryService.ZoneSnapshot;
import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import com.machineguard.platform.environmentalmonitoring.domain.services.ZoneConditionService;
import com.machineguard.platform.environmentalmonitoring.interfaces.rest.resources.EnvironmentalMonitoringResources.*;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.*;
import com.machineguard.platform.environmentalmonitoring.application.*;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.SensorNodeRepository;

@Component
public class EnvironmentalMonitoringResourceAssembler {
    private final MeasurementFreshnessPolicy freshness;
    private final MonitoringEvidenceService evidence;
    private final SensorNodeRepository nodes;
    public EnvironmentalMonitoringResourceAssembler(MeasurementFreshnessPolicy freshness,MonitoringEvidenceService evidence,SensorNodeRepository nodes) {
        this.freshness=freshness;this.evidence=evidence;this.nodes=nodes;
    }
    public MeasurementResource toResource(Measurement m) {
        var node=m.getSensorNodeId()==null?Optional.<SensorNode>empty():nodes.findByIdAndMonitoringPointId(m.getSensorNodeId(),m.getMonitoringPointId());
        String state=m.getSource()!=MeasurementSource.SENSOR?"NON_SENSOR":node.isPresent()
                && node.get().getStatus()!=SensorNodeStatus.INACTIVE && freshness.fresh(m,node.get())?"FRESH":"STALE";
        return new MeasurementResource(m.getId(), m.getOrganizationId(), m.getMonitoringZoneId(), m.getMonitoringPointId(),
                m.getSensorNodeId(), m.getEnvironmentalVariable(), m.getMeasuredValue(), m.getRecordedAt(), m.getReceivedAt(),m.getSource(),state,
                node.isPresent()?freshness.window(node.get()):null,freshness.age(m.getRecordedAt()),freshness.age(m.getReceivedAt()));
    }
    public PointResource toResource(MonitoringPoint p) {
        return new PointResource(p.getId(), p.getName(), p.getLocation(), p.getStatus());
    }
    public SensorResource toResource(SensorNode n) { return sensor(n,List.of(),List.of()); }
    private SensorResource sensor(SensorNode n,List<Measurement> receipts,List<Measurement> physical) {
        var received=receipts.stream().filter(m->n.getId().equals(m.getSensorNodeId()))
                .max(Comparator.comparing(m->m.getReceivedAt().toInstant()));
        boolean online=received.isPresent() && freshness.receivedFresh(received.get(),n);
        boolean inactive=n.getStatus()==SensorNodeStatus.INACTIVE;
        var status=inactive?SensorNodeStatus.INACTIVE:online?SensorNodeStatus.ONLINE:SensorNodeStatus.OFFLINE;
        var capture=physical.stream().filter(m->n.getId().equals(m.getSensorNodeId())).map(Measurement::getRecordedAt)
                .max(OffsetDateTime.timeLineOrder()).orElse(null);
        return new SensorResource(n.getId(),n.getMonitoringPointId(),n.getDeviceCode(),n.getSamplingIntervalSeconds(),status,capture,
                inactive?"INACTIVE":"ACTIVE",inactive?"INACTIVE":online?"ONLINE":"OFFLINE",
                received.map(Measurement::getReceivedAt).orElse(null),received.map(m->freshness.age(m.getReceivedAt())).orElse(null),freshness.window(n));
    }
    public ThresholdResource toResource(Threshold t) {
        return new ThresholdResource(t.getId(), t.getEnvironmentalVariable(), t.getMinimumValue(), t.getMaximumValue());
    }
    public ZoneResource toResource(ZoneSnapshot snapshot) {
        var z = snapshot.zone();
        var assessments=List.of(evidence.assess(z.getOrganizationId(),z.getId(),EnvironmentalVariable.TEMPERATURE),
                evidence.assess(z.getOrganizationId(),z.getId(),EnvironmentalVariable.HUMIDITY));
        var reasons=assessments.stream().flatMap(a->a.reasons().stream()).toList();
        String state=reasons.isEmpty()?"NORMAL":reasons.contains("OUT_OF_RANGE")?"OUT_OF_RANGE":
                reasons.contains("STALE") || reasons.contains("MISSING_POINT")?"STALE":"NO_DATA";
        var sensors=snapshot.nodes().stream().map(n->sensor(n,snapshot.physicalReceipts(),snapshot.physicalMeasurements())).toList();
        return new ZoneResource(z.getId(),z.getOrganizationId(),z.getFacilityId(),z.getName(),z.getDescription(),z.getStatus(),state,
                (int)sensors.stream().filter(n->n.status()==SensorNodeStatus.ONLINE).count(),
                (int)sensors.stream().filter(n->n.status()==SensorNodeStatus.OFFLINE).count(),
                (int)sensors.stream().filter(n->n.status()==SensorNodeStatus.INACTIVE).count(),
                snapshot.physicalReceipts().stream().map(Measurement::getReceivedAt).max(OffsetDateTime.timeLineOrder()).orElse(null),
                snapshot.points().stream().map(this::toResource).toList(),sensors,
                snapshot.thresholds().stream().map(this::toResource).toList(),
                snapshot.latestMeasurements().stream().map(this::toResource).toList(),
                reasons.isEmpty()?"FRESH":reasons.stream().allMatch(r->r.equals("OUT_OF_RANGE"))?"FRESH":"STALE",
                snapshot.physicalMeasurements().stream().map(this::toResource).toList());
    }
}
