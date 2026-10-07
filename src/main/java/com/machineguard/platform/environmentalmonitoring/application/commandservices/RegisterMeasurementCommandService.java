package com.machineguard.platform.environmentalmonitoring.application.commandservices;

import com.machineguard.platform.environmentalmonitoring.domain.commands.RegisterMeasurementCommand;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.*;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.*;
import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.*;
import com.machineguard.platform.environmentalmonitoring.application.ports.EnvironmentalMonitoringEventPublisher;
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
    public RegisterMeasurementCommandService(MonitoringZoneRepository zones, MonitoringPointRepository points,
            SensorNodeRepository nodes, ThresholdRepository thresholds, MeasurementRepository measurements,
            EnvironmentalMonitoringEventPublisher events, Clock clock) {
        this.zones = zones;
        this.points = points;
        this.nodes = nodes;
        this.thresholds = thresholds;
        this.measurements = measurements;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Measurement handle(RegisterMeasurementCommand command) {
        // Serialize zone ingestion: concurrent points must not prematurely restore a zone-wide excursion.
        MonitoringZone zone = zones.lockByIdAndOrganizationId(command.monitoringZoneId(), command.organizationId())
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Monitoring zone not found"));
        MonitoringPoint point = points.findByIdAndMonitoringZoneId(command.monitoringPointId(), zone.getId())
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Monitoring point not found in zone"));
        if (zone.getStatus() != MonitoringZoneStatus.ACTIVE || point.getStatus() != MonitoringPointStatus.ACTIVE)
            throw new EnvironmentalMonitoringRuleException("Zone and point must be active");
        SensorNode node = command.sensorNodeId() == null ? null : nodes.findByIdAndMonitoringPointId(command.sensorNodeId(), point.getId())
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Sensor node not found in point"));
        if (node != null && node.getStatus() == SensorNodeStatus.INACTIVE)
            throw new EnvironmentalMonitoringRuleException("Sensor node is inactive");
        Threshold threshold = thresholds.findByMonitoringZoneIdAndEnvironmentalVariable(zone.getId(), command.environmentalVariable())
                .orElseThrow(() -> new EnvironmentalMonitoringRuleException("Threshold not configured for variable"));
        Measurement measurement = Measurement.record(command.organizationId(), zone.getId(), point.getId(),
                command.sensorNodeId(), command.environmentalVariable(), command.measuredValue(), command.recordedAt(),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        Measurement previous = measurements.findFirstByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableOrderByRecordedAtDesc(
                command.organizationId(), point.getId(), command.environmentalVariable()).orElse(null);
        if (measurements.existsByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableAndRecordedAt(
                command.organizationId(), point.getId(), command.environmentalVariable(), measurement.getRecordedAt()))
            throw new EnvironmentalMonitoringRuleException("A reading already exists for this point, variable and recordedAt");
        measurements.save(measurement);
        // Historical backfill is retained but cannot rewind live state or Traceability's lifecycle.
        if (previous != null && measurement.getRecordedAt().isBefore(previous.getRecordedAt())) return measurement;
        if (node != null) {
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
