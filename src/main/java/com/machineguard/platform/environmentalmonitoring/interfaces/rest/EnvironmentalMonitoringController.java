package com.machineguard.platform.environmentalmonitoring.interfaces.rest;

import com.machineguard.platform.environmentalmonitoring.application.commandservices.RegisterMeasurementCommandService;
import com.machineguard.platform.environmentalmonitoring.application.queryservices.EnvironmentalMonitoringQueryService;
import com.machineguard.platform.environmentalmonitoring.domain.commands.RegisterMeasurementCommand;
import com.machineguard.platform.environmentalmonitoring.domain.queries.EnvironmentalMonitoringQueries.*;
import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;
import com.machineguard.platform.environmentalmonitoring.interfaces.rest.resources.EnvironmentalMonitoringResources.*;
import com.machineguard.platform.environmentalmonitoring.interfaces.rest.assemblers.EnvironmentalMonitoringResourceAssembler;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/environmental-monitoring")
@Tag(name = "Environmental Monitoring")
@SecurityRequirement(name = "bearerAuth")
public class EnvironmentalMonitoringController {
    private final RegisterMeasurementCommandService commands;
    private final EnvironmentalMonitoringQueryService queries;
    private final EnvironmentalMonitoringResourceAssembler assembler;
    public EnvironmentalMonitoringController(RegisterMeasurementCommandService commands, EnvironmentalMonitoringQueryService queries,
            EnvironmentalMonitoringResourceAssembler assembler) {
        this.commands = commands;
        this.queries = queries;
        this.assembler = assembler;
    }
    @PostMapping("/measurements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MeasurementResource> register(@AuthenticationPrincipal IamPrincipal principal,
            @Valid @RequestBody RegisterMeasurementResource resource) {
        var result = commands.handle(new RegisterMeasurementCommand(principal.organizationId(), resource.monitoringZoneId(),
                resource.monitoringPointId(), resource.sensorNodeId(), resource.environmentalVariable(), resource.measuredValue(), resource.recordedAt()));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(result.getId()).toUri())
                .body(assembler.toResource(result));
    }
    @GetMapping("/measurements/latest")
    public List<MeasurementResource> latest(@AuthenticationPrincipal IamPrincipal principal, @RequestParam(required = false) UUID monitoringZoneId) {
        return queries.handle(new GetLatestMeasurements(principal.organizationId(), monitoringZoneId)).stream().map(assembler::toResource).toList();
    }
    @GetMapping("/measurements/history")
    public MeasurementHistoryResource history(@AuthenticationPrincipal IamPrincipal principal, @RequestParam UUID monitoringZoneId,
            @RequestParam(required = false) UUID monitoringPointId, @RequestParam(required = false) EnvironmentalVariable environmentalVariable,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        var result = queries.handle(new GetMeasurementHistory(principal.organizationId(), monitoringZoneId, monitoringPointId,
                environmentalVariable, from, to, page, size));
        return new MeasurementHistoryResource(result.items().stream().map(assembler::toResource).toList(), page, size, result.totalElements(), result.totalPages());
    }
    @GetMapping("/measurements/{id}")
    public MeasurementResource measurement(@AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID id) {
        return assembler.toResource(queries.handle(new GetMeasurement(principal.organizationId(), id)));
    }
    @GetMapping("/zones")
    public List<ZoneResource> zones(@AuthenticationPrincipal IamPrincipal principal) {
        return queries.handle(new GetZones(principal.organizationId())).stream().map(assembler::toResource).toList();
    }
    @GetMapping("/zones/{id}")
    public ZoneResource zone(@AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID id) {
        return assembler.toResource(queries.handle(new GetZone(principal.organizationId(), id)));
    }
}
