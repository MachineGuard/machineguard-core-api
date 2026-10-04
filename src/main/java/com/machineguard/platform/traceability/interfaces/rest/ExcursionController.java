package com.machineguard.platform.traceability.interfaces.rest;

import com.machineguard.platform.traceability.application.commandservices.RegisterNonConformityCommandService;
import com.machineguard.platform.traceability.application.queryservices.GetExcursionHistoryQueryService;
import com.machineguard.platform.traceability.domain.commands.ChangeDispositionCommand;
import com.machineguard.platform.traceability.domain.commands.RegisterNonConformityCommand;
import com.machineguard.platform.traceability.domain.model.ExcursionStatus;
import com.machineguard.platform.traceability.domain.queries.GetExcursionHistoryQuery;
import com.machineguard.platform.traceability.interfaces.rest.assemblers.ResourceAssemblers;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.ExcursionDetailResource;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.ExcursionResource;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.NonConformityResource;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.RegisterNonConformityResource;
import com.machineguard.platform.traceability.interfaces.rest.resources.TraceabilityResources.UpdateDispositionResource;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;


@RestController
@RequestMapping("/api/v1/traceability")
@Tag(name = "Excursions and Non-conformities", description = "Historial de excursiones ambientales y no conformidades de lotes")
@SecurityRequirement(name = "bearerAuth")
public class ExcursionController {

    private final GetExcursionHistoryQueryService queries;
    private final RegisterNonConformityCommandService nonConformityCommands;
    private final ResourceAssemblers assemblers;

    public ExcursionController(GetExcursionHistoryQueryService queries,
                               RegisterNonConformityCommandService nonConformityCommands,
                               ResourceAssemblers assemblers) {
        this.queries = queries;
        this.nonConformityCommands = nonConformityCommands;
        this.assemblers = assemblers;
    }

    @GetMapping("/excursions")
    @Operation(summary = "Historial de excursiones de la organizacion, filtrable por zona, periodo y estado")
    public List<ExcursionResource> history(
            @AuthenticationPrincipal IamPrincipal principal,
            @RequestParam(required = false) UUID monitoringZoneId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) ExcursionStatus status) {
        return queries.handle(new GetExcursionHistoryQuery(principal.organizationId(), monitoringZoneId, from, to, status))
                .stream().map(assemblers::excursion).toList();
    }

    @GetMapping("/excursions/open")
    @Operation(summary = "Excursiones abiertas en este momento")
    public List<ExcursionResource> open(@AuthenticationPrincipal IamPrincipal principal) {
        return queries.findOpen(principal.organizationId()).stream().map(assemblers::excursion).toList();
    }

    @GetMapping("/excursions/{excursionId}")
    @Operation(summary = "Detalle de una excursion con sus no conformidades")
    public ExcursionDetailResource detail(@AuthenticationPrincipal IamPrincipal principal,
                                          @PathVariable UUID excursionId) {
        return assemblers.excursionDetail(queries.getById(principal.organizationId(), excursionId),
                queries.nonConformitiesOf(principal.organizationId(), excursionId));
    }

    @PostMapping("/excursions/{excursionId}/non-conformities")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar una no conformidad sobre un lote afectado (ADMIN)")
    public ResponseEntity<NonConformityResource> registerNonConformity(
            @AuthenticationPrincipal IamPrincipal principal,
            @PathVariable UUID excursionId, @Valid @RequestBody RegisterNonConformityResource body) {
        var created = nonConformityCommands.handle(new RegisterNonConformityCommand(principal.organizationId(), excursionId,
                body.batchCode(), body.productName(), body.quantity(), body.storedAt(), body.classification(), principal.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(assemblers.nonConformity(created));
    }

    @GetMapping("/excursions/{excursionId}/non-conformities")
    @Operation(summary = "No conformidades de una excursion")
    public List<NonConformityResource> nonConformities(@AuthenticationPrincipal IamPrincipal principal,
                                                       @PathVariable UUID excursionId) {
        return queries.nonConformitiesOf(principal.organizationId(), excursionId).stream().map(assemblers::nonConformity).toList();
    }

    @PatchMapping("/non-conformities/{nonConformityId}/disposition")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar la disposicion del lote (ADMIN); liberar exige justificacion")
    public NonConformityResource updateDisposition(@AuthenticationPrincipal IamPrincipal principal,
                                                   @PathVariable UUID nonConformityId,
                                                   @Valid @RequestBody UpdateDispositionResource body) {
        var updated = nonConformityCommands.handle(
                new ChangeDispositionCommand(principal.organizationId(), nonConformityId, body.disposition(), body.justification()));
        return assemblers.nonConformity(updated);
    }
}
