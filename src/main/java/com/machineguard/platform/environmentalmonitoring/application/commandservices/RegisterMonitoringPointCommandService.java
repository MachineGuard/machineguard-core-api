package com.machineguard.platform.environmentalmonitoring.application.commandservices;

import com.machineguard.platform.environmentalmonitoring.domain.commands.RegisterMonitoringPointCommand;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.EnvironmentalMonitoringNotFoundException;
import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class RegisterMonitoringPointCommandService {
    private final MonitoringZoneRepository zones;
    private final MonitoringPointRepository points;
    private final Clock clock;

    public RegisterMonitoringPointCommandService(MonitoringZoneRepository zones, MonitoringPointRepository points, Clock clock) {
        this.zones = zones;
        this.points = points;
        this.clock = clock;
    }

    @Transactional
    public MonitoringPoint handle(RegisterMonitoringPointCommand command) {
        MonitoringZone zone = zones.findByIdAndOrganizationId(command.monitoringZoneId(), command.organizationId())
                .orElseThrow(() -> new EnvironmentalMonitoringNotFoundException("Monitoring zone not found"));
        return points.save(MonitoringPoint.register(zone.getId(), command.name().trim(), command.location(),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
    }
}
