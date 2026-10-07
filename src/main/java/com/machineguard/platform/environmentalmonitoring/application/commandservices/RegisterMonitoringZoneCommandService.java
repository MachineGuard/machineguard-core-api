package com.machineguard.platform.environmentalmonitoring.application.commandservices;

import com.machineguard.platform.environmentalmonitoring.domain.commands.RegisterMonitoringZoneCommand;
import com.machineguard.platform.environmentalmonitoring.domain.model.MonitoringZone;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.MonitoringZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class RegisterMonitoringZoneCommandService {
    private final MonitoringZoneRepository zones;
    private final Clock clock;

    public RegisterMonitoringZoneCommandService(MonitoringZoneRepository zones, Clock clock) {
        this.zones = zones;
        this.clock = clock;
    }

    @Transactional
    public MonitoringZone handle(RegisterMonitoringZoneCommand command) {
        return zones.save(MonitoringZone.register(command.organizationId(), command.facilityId(), command.name().trim(),
                command.description(), OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
    }
}
