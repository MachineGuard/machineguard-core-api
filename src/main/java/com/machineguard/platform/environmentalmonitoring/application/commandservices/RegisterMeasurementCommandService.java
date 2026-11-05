package com.machineguard.platform.environmentalmonitoring.application.commandservices;

import com.machineguard.platform.environmentalmonitoring.domain.commands.RegisterMeasurementCommand;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.*;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.*;
import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.*;
import com.machineguard.platform.environmentalmonitoring.application.ports.EnvironmentalMonitoringEventPublisher;
import com.machineguard.platform.environmentalmonitoring.application.MeasurementTimePolicy;
import com.machineguard.platform.environmentalmonitoring.application.MonitoringEvidenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class RegisterMeasurementCommandService {
    private final MonitoringZoneRepository zones;
    private final MonitoringPointRepository points;
    private final SensorNodeRepository nodes;
    private final ThresholdRepository thresholds;
    private final MeasurementRepository measurements;
    private final EnvironmentalMonitoringEventPublisher events;
    private final Clock clock;
    private final MeasurementTimePolicy timePolicy;
    private final MonitoringEvidenceService evidence;
    public RegisterMeasurementCommandService(MonitoringZoneRepository zones, MonitoringPointRepository points,
            SensorNodeRepository nodes, ThresholdRepository thresholds, MeasurementRepository measurements,
            EnvironmentalMonitoringEventPublisher events, Clock clock, MeasurementTimePolicy timePolicy,MonitoringEvidenceService evidence) {
        this.zones = zones;
        this.points = points;
        this.nodes = nodes;
        this.thresholds = thresholds;
        this.measurements = measurements;
        this.events = events;
        this.clock = clock;
        this.timePolicy = timePolicy;
        this.evidence=evidence;
    }

    @Transactional
    public Measurement handle(RegisterMeasurementCommand command) {
        return register(command,MeasurementSource.MANUAL);
    }
    @Transactional
    public Measurement handleSensor(RegisterMeasurementCommand command) { return register(command,MeasurementSource.SENSOR); }
    private Measurement register(RegisterMeasurementCommand command,MeasurementSource source) {
        timePolicy.validate(command.recordedAt());
        // Serialize zone ingestion: concurrent points must not prematurely restore a zone-wide excursion.
        MonitoringZone zone = zones.lockByIdAndOrganizationId(command.monitoringZoneId(), command.organizationId())
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Monitoring zone not found"));
        MonitoringPoint point = points.findByIdAndMonitoringZoneId(command.monitoringPointId(), zone.getId())
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Monitoring point not found in zone"));
        if (zone.getStatus() != MonitoringZoneStatus.ACTIVE || point.getStatus() != MonitoringPointStatus.ACTIVE)
            throw new EnvironmentalMonitoringRuleException(EnvironmentalMonitoringRuleException.Reason.NODE_INACTIVE,"Zone and point must be active");
        SensorNode node = command.sensorNodeId() == null ? null : nodes.findByIdAndMonitoringPointId(command.sensorNodeId(), point.getId())
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Sensor node not found in point"));
        if (node != null && node.getStatus() == SensorNodeStatus.INACTIVE)
            throw new EnvironmentalMonitoringRuleException(EnvironmentalMonitoringRuleException.Reason.NODE_INACTIVE,"Sensor node is inactive");
        Threshold threshold = thresholds.findByMonitoringZoneIdAndEnvironmentalVariable(zone.getId(), command.environmentalVariable())
                .orElseThrow(() -> new EnvironmentalMonitoringRuleException(EnvironmentalMonitoringRuleException.Reason.THRESHOLD_NOT_CONFIGURED,"Threshold not configured for variable"));
        Measurement measurement = Measurement.record(command.organizationId(), zone.getId(), point.getId(),
                command.sensorNodeId(), command.environmentalVariable(), command.measuredValue(), command.recordedAt(),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),source);
        var eligibleSensors=nodes.findByMonitoringPointIdIn(java.util.List.of(point.getId())).stream()
                .filter(n->n.getStatus()!=SensorNodeStatus.INACTIVE).map(SensorNode::getId).collect(java.util.stream.Collectors.toSet());
        Measurement previous = source==MeasurementSource.SENSOR
                ? measurements.findLatestSensorMeasurements(command.organizationId(),zone.getId()).stream()
                    .filter(m->m.getMonitoringPointId().equals(point.getId()) && m.getEnvironmentalVariable()==command.environmentalVariable()
                        && (m.getSensorNodeId()==null || eligibleSensors.contains(m.getSensorNodeId())))
                    .max(java.util.Comparator.comparing(m->m.getRecordedAt().toInstant())).orElse(null)
                : measurements.findFirstByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableOrderByRecordedAtDesc(
                    command.organizationId(),point.getId(),command.environmentalVariable()).orElse(null);
        if (measurements.existsByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableAndRecordedAt(
                command.organizationId(), point.getId(), command.environmentalVariable(), measurement.getRecordedAt()))
            throw new EnvironmentalMonitoringRuleException(EnvironmentalMonitoringRuleException.Reason.READING_CONFLICT,"A reading already exists for this point, variable and recordedAt");
        var before=source==MeasurementSource.SENSOR
                ? evidence.assess(command.organizationId(),zone.getId(),command.environmentalVariable()):null;
        measurements.save(measurement);
        // Backfill has a separate event: it cannot rewind live state or physical excursions.
        if (previous != null && measurement.getRecordedAt().isBefore(previous.getRecordedAt())) {
            if (threshold.isOutOfRange(measurement.getMeasuredValue()))
                events.publish(new LateDeviationDetected(measurement.getId(),command.organizationId(),zone.getId(),point.getId(),
                        command.sensorNodeId(),command.environmentalVariable(),measurement.getMeasuredValue(),
                        threshold.getMinimumValue(),threshold.getMaximumValue(),measurement.getRecordedAt(),measurement.getReceivedAt()));
            return measurement;
        }
        if (node != null && source==MeasurementSource.SENSOR) {
            node.recordMeasurement(measurement.getRecordedAt());
            nodes.save(node);
        }
        if (threshold.isOutOfRange(measurement.getMeasuredValue())) {
            // Existing Traceability contract requires every current deviation to update the open excursion peak.
            events.publish(new DeviationDetected(measurement.getId(), command.organizationId(), zone.getId(), point.getId(),
                    command.sensorNodeId(), command.environmentalVariable(), measurement.getMeasuredValue(),
                    threshold.getMinimumValue(), threshold.getMaximumValue(), measurement.getRecordedAt()));
        } else if (source==MeasurementSource.SENSOR && !before.withinSafeRange()
                && (previous!=null || !before.selectedMeasurements().isEmpty())) {
            // Only a current physical reading can restore coverage. Reuse exactly the
            // same selected, fresh SENSOR evidence as incident resolution, including its dates.
            var recovered=evidence.assess(command.organizationId(),zone.getId(),command.environmentalVariable());
            if (recovered.withinSafeRange()) {
                OffsetDateTime restoredAt=recovered.selectedMeasurements().stream().map(Measurement::getRecordedAt)
                        .max(OffsetDateTime.timeLineOrder()).orElseThrow();
                events.publish(new SafeRangeRestored(measurement.getId(), command.organizationId(), zone.getId(), point.getId(),
                        command.sensorNodeId(), command.environmentalVariable(), measurement.getMeasuredValue(),
                        measurement.getRecordedAt(), restoredAt));
            }
        }
        return measurement;
    }
}
