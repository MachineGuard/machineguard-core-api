package com.machineguard.platform.traceability.interfaces.rest;

import com.machineguard.platform.traceability.application.queryservices.GetMeasurementHistoryQueryService;
import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;
import com.machineguard.platform.traceability.domain.queries.GetMeasurementHistoryQuery;
import com.machineguard.platform.traceability.interfaces.rest.assemblers.ResourceAssemblers;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.MeasurementHistoryResource;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/traceability")
@Tag(name = "Measurement History", description = "Historial de mediciones que sustenta la evidencia de trazabilidad")
@SecurityRequirement(name = "bearerAuth")
public class MeasurementHistoryController {

    private final GetMeasurementHistoryQueryService queries;
    private final ResourceAssemblers assemblers;

    public MeasurementHistoryController(GetMeasurementHistoryQueryService queries, ResourceAssemblers assemblers) {
        this.queries = queries;
        this.assemblers = assemblers;
    }

    @GetMapping("/measurement-history")
    @Operation(summary = "Measurement History de una zona dentro de un periodo")
    public MeasurementHistoryResource byZone(
            @AuthenticationPrincipal IamPrincipal principal, @RequestParam UUID monitoringZoneId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) EnvironmentalVariable variable) {
        var points = queries.handle(new GetMeasurementHistoryQuery(principal.organizationId(), monitoringZoneId, variable, from, to));
        return assemblers.measurementHistory(monitoringZoneId, from, to, points);
    }

    @GetMapping("/excursions/{excursionId}/measurement-history")
    @Operation(summary = "Measurement History asociado a una excursion")
    public MeasurementHistoryResource byExcursion(@AuthenticationPrincipal IamPrincipal principal,
                                                  @PathVariable UUID excursionId) {
        var points = queries.handleForExcursion(principal.organizationId(), excursionId);
        Instant from = points.isEmpty() ? null : points.get(0).recordedAt();
        Instant to = points.isEmpty() ? null : points.get(points.size() - 1).recordedAt();
        return assemblers.measurementHistory(null, from, to, points);
    }
}
