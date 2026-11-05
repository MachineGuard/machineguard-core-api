package com.machineguard.platform.environmentalmonitoring.application.commandservices;

import com.machineguard.platform.environmentalmonitoring.domain.commands.RegisterMeasurementCommand;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.*;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.*;
import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.*;
import com.machineguard.platform.environmentalmonitoring.application.ports.EnvironmentalMonitoringEventPublisher;
import com.machineguard.platform.environmentalmonitoring.application.MeasurementTimePolicy;
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
    public RegisterMeasurementCommandService(MonitoringZoneRepository zones, MonitoringPointRepository points,
            SensorNodeRepository nodes, ThresholdRepository thresholds, MeasurementRepository measurements,
            EnvironmentalMonitoringEventPublisher events, Clock clock, MeasurementTimePolicy timePolicy) {
        this.zones = zones;
        this.points = points;
        this.nodes = nodes;
        this.thresholds = thresholds;
        this.measurements = measurements;
        this.events = events;
        this.clock = clock;
        this.timePolicy = timePolicy;
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
        Measurement previous = source==MeasurementSource.SENSOR
                ? measurements.findLatestSensorMeasurements(command.organizationId(),zone.getId()).stream()
                    .filter(m->m.getMonitoringPointId().equals(point.getId()) && m.getEnvironmentalVariable()==command.environmentalVariable())
                    .max(java.util.Comparator.comparing(m->m.getRecordedAt().toInstant())).orElse(null)
                : measurements.findFirstByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableOrderByRecordedAtDesc(
                    command.organizationId(),point.getId(),command.environmentalVariable()).orElse(null);
        if (measurements.existsByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableAndRecordedAt(
                command.organizationId(), point.getId(), command.environmentalVariable(), measurement.getRecordedAt()))
            throw new EnvironmentalMonitoringRuleException(EnvironmentalMonitoringRuleException.Reason.READING_CONFLICT,"A reading already exists for this point, variable and recordedAt");
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
        } else if (previous != null && threshold.isOutOfRange(previous.getMeasuredValue())) {
            var latest = measurements.findLatest(command.organizationId(), zone.getId()).stream()
                    .filter(m -> m.getEnvironmentalVariable() == command.environmentalVariable()).toList();
            if (latest.stream().allMatch(m -> threshold.contains(m.getMeasuredValue()))) {
                OffsetDateTime restoredAt = latest.stream().map(Measurement::getRecordedAt)
                        .max(OffsetDateTime.timeLineOrder()).orElse(measurement.getRecordedAt());
                events.publish(new SafeRangeRestored(measurement.getId(), command.organizationId(), zone.getId(), point.getId(),
                        command.sensorNodeId(), command.environmentalVariable(), measurement.getMeasuredValue(),
                        measurement.getRecordedAt(), restoredAt));
            }
        }
        return measurement;
    }
}
