package com.machineguard.platform.environmentalmonitoring.interfaces.rest;

import com.machineguard.platform.environmentalmonitoring.application.commandservices.*;
import com.machineguard.platform.environmentalmonitoring.application.queryservices.EnvironmentalMonitoringQueryService;
import com.machineguard.platform.environmentalmonitoring.domain.commands.*;
import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;
import com.machineguard.platform.environmentalmonitoring.domain.queries.EnvironmentalMonitoringQueries.GetZone;
import com.machineguard.platform.environmentalmonitoring.interfaces.rest.assemblers.EnvironmentalMonitoringResourceAssembler;
import com.machineguard.platform.environmentalmonitoring.interfaces.rest.resources.EnvironmentalMonitoringResources.*;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.UUID;

/** Setup of zones, points, sensor nodes and Safe Ranges; every operation is limited to the tenant in the JWT. */
@RestController
@RequestMapping("/api/v1/environmental-monitoring/zones")
@Tag(name = "Environmental Monitoring")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class MonitoringConfigurationController {
    private final RegisterMonitoringZoneCommandService zones;
    private final RegisterMonitoringPointCommandService points;
    private final RegisterSensorNodeCommandService sensors;
    private final ConfigureThresholdCommandService thresholds;
    private final EnvironmentalMonitoringQueryService queries;
    private final EnvironmentalMonitoringResourceAssembler assembler;
    public MonitoringConfigurationController(RegisterMonitoringZoneCommandService zones, RegisterMonitoringPointCommandService points,
            RegisterSensorNodeCommandService sensors, ConfigureThresholdCommandService thresholds,
            EnvironmentalMonitoringQueryService queries, EnvironmentalMonitoringResourceAssembler assembler) {
        this.zones = zones;
        this.points = points;
        this.sensors = sensors;
        this.thresholds = thresholds;
        this.queries = queries;
        this.assembler = assembler;
    }
    @PostMapping
    @Operation(summary = "Registra una Monitoring Zone en la organización autenticada")
    public ResponseEntity<ZoneResource> registerZone(@AuthenticationPrincipal IamPrincipal principal,
            @Valid @RequestBody RegisterMonitoringZoneResource resource) {
        var zone = zones.handle(new RegisterMonitoringZoneCommand(principal.organizationId(), resource.facilityId(),
                resource.name(), resource.description()));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(zone.getId()).toUri())
                .body(assembler.toResource(queries.handle(new GetZone(principal.organizationId(), zone.getId()))));
    }
    @PostMapping("/{zoneId}/points")
    @Operation(summary = "Registra un Monitoring Point en la zona")
    public ResponseEntity<PointResource> registerPoint(@AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID zoneId,
            @Valid @RequestBody RegisterMonitoringPointResource resource) {
        var point = points.handle(new RegisterMonitoringPointCommand(principal.organizationId(), zoneId, resource.name(), resource.location()));
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toResource(point));
    }
    @PostMapping("/{zoneId}/points/{pointId}/sensors")
    @Operation(summary = "Registra un Sensor Node en el punto de monitoreo")
    public ResponseEntity<SensorResource> registerSensor(@AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID zoneId,
            @PathVariable UUID pointId, @Valid @RequestBody RegisterSensorNodeResource resource) {
        var node = sensors.handle(new RegisterSensorNodeCommand(principal.organizationId(), zoneId, pointId,
                resource.deviceCode(), resource.samplingIntervalSeconds()));
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toResource(node));
    }
    @PutMapping("/{zoneId}/thresholds/{environmentalVariable}")
    @Operation(summary = "Configura o reemplaza el Safe Range de la zona para una variable ambiental")
    public ThresholdResource configureThreshold(@AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID zoneId,
            @PathVariable EnvironmentalVariable environmentalVariable, @Valid @RequestBody ConfigureThresholdResource resource) {
        return assembler.toResource(thresholds.handle(new ConfigureThresholdCommand(principal.organizationId(), zoneId,
                environmentalVariable, resource.minimumValue(), resource.maximumValue())));
    }
}
