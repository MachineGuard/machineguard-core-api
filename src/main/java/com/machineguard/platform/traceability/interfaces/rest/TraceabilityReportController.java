package com.machineguard.platform.traceability.interfaces.rest;

import com.machineguard.platform.traceability.application.commandservices.GenerateTraceabilityReportCommandService;
import com.machineguard.platform.traceability.application.queryservices.GetTraceabilityReportQueryService;
import com.machineguard.platform.traceability.domain.commands.GenerateTraceabilityReportCommand;
import com.machineguard.platform.traceability.domain.queries.GetTraceabilityReportQuery;
import com.machineguard.platform.traceability.interfaces.rest.assemblers.ResourceAssemblers;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.GenerateTraceabilityReportResource;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.TraceabilityReportResource;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/traceability/reports")
@Tag(name = "Traceability Reports", description = "Reportes de trazabilidad sellados para auditorias (HACCP, ISO 9001)")
@SecurityRequirement(name = "bearerAuth")
public class TraceabilityReportController {

    private final GenerateTraceabilityReportCommandService commands;
    private final GetTraceabilityReportQueryService queries;
    private final ResourceAssemblers assemblers;

    public TraceabilityReportController(GenerateTraceabilityReportCommandService commands,
                                        GetTraceabilityReportQueryService queries, ResourceAssemblers assemblers) {
        this.commands = commands;
        this.queries = queries;
        this.assemblers = assemblers;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Generar y sellar un reporte de trazabilidad (ADMIN)")
    public ResponseEntity<TraceabilityReportResource> generate(
            @AuthenticationPrincipal IamPrincipal principal,
            @Valid @RequestBody GenerateTraceabilityReportResource body) {
        var report = commands.handle(new GenerateTraceabilityReportCommand(principal.organizationId(), body.monitoringZoneId(),
                body.periodStart(), body.periodEnd(), principal.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(assemblers.reportWithContent(report));
    }

    @GetMapping
    @Operation(summary = "Listar los reportes de la organizacion (sin contenido)")
    public List<TraceabilityReportResource> list(@AuthenticationPrincipal IamPrincipal principal,
                                                 @RequestParam(required = false) UUID monitoringZoneId) {
        return queries.list(principal.organizationId(), monitoringZoneId).stream().map(assemblers::reportSummary).toList();
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Consultar un reporte con su contenido y la verificacion de integridad")
    public TraceabilityReportResource get(@AuthenticationPrincipal IamPrincipal principal,
                                          @PathVariable UUID reportId) {
        return assemblers.reportWithContent(queries.handle(new GetTraceabilityReportQuery(principal.organizationId(), reportId)));
    }

    @GetMapping("/{reportId}/download")
    @Operation(summary = "Descargar el reporte como archivo JSON para el ERP del cliente")
    public ResponseEntity<TraceabilityReportResource> download(@AuthenticationPrincipal IamPrincipal principal,
                                                               @PathVariable UUID reportId) {
        var resource = assemblers.reportWithContent(queries.handle(new GetTraceabilityReportQuery(principal.organizationId(), reportId)));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("traceability-report-" + reportId + ".json").build().toString())
                .body(resource);
    }
}
