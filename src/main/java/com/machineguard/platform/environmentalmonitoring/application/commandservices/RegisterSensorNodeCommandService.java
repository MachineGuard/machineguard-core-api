package com.machineguard.platform.environmentalmonitoring.application.commandservices;

import com.machineguard.platform.environmentalmonitoring.domain.commands.RegisterSensorNodeCommand;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.*;
import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class RegisterSensorNodeCommandService {
    private final MonitoringZoneRepository zones;
    private final MonitoringPointRepository points;
    private final SensorNodeRepository nodes;
    private final Clock clock;

    public RegisterSensorNodeCommandService(MonitoringZoneRepository zones, MonitoringPointRepository points,
            SensorNodeRepository nodes, Clock clock) {
        this.zones = zones;
        this.points = points;
        this.nodes = nodes;
        this.clock = clock;
    }

    @Transactional
    public SensorNode handle(RegisterSensorNodeCommand command) {
        MonitoringZone zone = zones.findByIdAndOrganizationId(command.monitoringZoneId(), command.organizationId())
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Monitoring zone not found"));
        MonitoringPoint point = points.findByIdAndMonitoringZoneId(command.monitoringPointId(), zone.getId())
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Monitoring point not found in zone"));
        String deviceCode = command.deviceCode().trim();
        // device_code is unique across tenants: it identifies the physical node.
        if (nodes.existsByDeviceCode(deviceCode))
            throw new EnvironmentalMonitoringRuleException("A sensor node already uses this device code");
        return nodes.save(SensorNode.register(point.getId(), deviceCode, command.samplingIntervalSeconds(),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
    }
}
