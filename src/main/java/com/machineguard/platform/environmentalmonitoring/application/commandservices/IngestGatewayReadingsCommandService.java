package com.machineguard.platform.environmentalmonitoring.application.commandservices;

import com.machineguard.platform.environmentalmonitoring.application.ports.GatewayDeviceResolver;
import com.machineguard.platform.environmentalmonitoring.domain.commands.RegisterMeasurementCommand;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.EnvironmentalMonitoringNotFoundException;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.EnvironmentalMonitoringRuleException;
import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.MeasurementRepository;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.MonitoringZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Transport-level idempotency and authorization; measurement evaluation remains in the existing producer service. */
@Service
public class IngestGatewayReadingsCommandService {
    private final GatewayDeviceResolver devices;
    private final MonitoringZoneRepository zones;
    private final MeasurementRepository measurements;
    private final RegisterMeasurementCommandService register;
    private final Clock clock;
    public IngestGatewayReadingsCommandService(GatewayDeviceResolver devices,MonitoringZoneRepository zones,
            MeasurementRepository measurements,RegisterMeasurementCommandService register,Clock clock) {
        this.devices=devices; this.zones=zones; this.measurements=measurements; this.register=register; this.clock=clock;
    }
    public record Reading(String deviceCode,BigDecimal temperature,BigDecimal humidity,OffsetDateTime capturedAt) { }
    public record Command(UUID organizationId,Set<UUID> authorizedSensorNodeIds,List<Reading> readings) {
        public Command { authorizedSensorNodeIds=Set.copyOf(authorizedSensorNodeIds); readings=List.copyOf(readings); }
    }
    public record Receipt(int readingsAccepted,int measurementsCreated,int measurementsReused) { }
    private record Resolved(Reading reading,GatewayDeviceResolver.Binding device) { }

    @Transactional
    public Receipt handle(Command command) {
        if (command.readings().isEmpty() || command.readings().size()>500)
            throw new IllegalArgumentException("A batch requires 1 to 500 readings");
        List<Resolved> resolved=new ArrayList<>();
        for (Reading reading:command.readings()) {
            if (reading.capturedAt().toInstant().isAfter(clock.instant().plusSeconds(300)))
                throw new IllegalArgumentException("capturedAt cannot be more than five minutes in the future");
            var device=devices.resolve(command.organizationId(),reading.deviceCode())
                    .orElseThrow(()->new EnvironmentalMonitoringNotFoundException("Sensor node not authorized"));
            if (!command.authorizedSensorNodeIds().contains(device.sensorNodeId()))
                throw new EnvironmentalMonitoringNotFoundException("Sensor node not authorized");
            resolved.add(new Resolved(reading,device));
        }
        // A stable lock order prevents deadlocks when concurrent batches contain zones in opposite order.
        resolved.stream().map(r->r.device().monitoringZoneId()).distinct().sorted().forEach(zone ->
                zones.lockByIdAndOrganizationId(zone,command.organizationId())
                        .orElseThrow(()->new EnvironmentalMonitoringNotFoundException("Monitoring zone not found")));
        resolved.sort(Comparator.comparing((Resolved r)->r.reading().capturedAt().toInstant())
                .thenComparing(r->r.reading().deviceCode()));
        int created=0,reused=0;
        for (Resolved item:resolved) {
            Reading reading=item.reading(); var device=item.device();
            OffsetDateTime at=reading.capturedAt().withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
            for (EnvironmentalVariable variable:List.of(EnvironmentalVariable.TEMPERATURE,EnvironmentalVariable.HUMIDITY)) {
                BigDecimal value=variable==EnvironmentalVariable.TEMPERATURE?reading.temperature():reading.humidity();
                var existing=measurements.findByOrganizationIdAndMonitoringPointIdAndEnvironmentalVariableAndRecordedAt(
                        command.organizationId(),device.monitoringPointId(),variable,at);
                if (existing.isPresent()) {
                    var saved=existing.get();
                    if (saved.getMeasuredValue().compareTo(value)!=0 || !Objects.equals(saved.getSensorNodeId(),device.sensorNodeId()))
                        throw new EnvironmentalMonitoringRuleException("A different reading already exists at capturedAt");
                    reused++;
                } else {
                    register.handle(new RegisterMeasurementCommand(command.organizationId(),device.monitoringZoneId(),
                            device.monitoringPointId(),device.sensorNodeId(),variable,value,at));
                    created++;
                }
            }
        }
        return new Receipt(resolved.size(),created,reused);
    }
}
