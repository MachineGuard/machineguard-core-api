package com.machineguard.platform.alertincident.interfaces.rest;

import com.machineguard.platform.alertincident.application.commandservices.RegisterCorrectiveActionCommandService;
import com.machineguard.platform.alertincident.application.commandservices.ResolveIncidentCommandService;
import com.machineguard.platform.alertincident.application.queryservices.IncidentQueryService;
import com.machineguard.platform.alertincident.domain.commands.AlertIncidentCommands.RegisterCorrectiveActionCommand;
import com.machineguard.platform.alertincident.domain.commands.AlertIncidentCommands.ResolveIncidentCommand;
import com.machineguard.platform.alertincident.domain.model.IncidentStatus;
import com.machineguard.platform.alertincident.domain.queries.AlertIncidentQueries.GetIncidentByIdQuery;
import com.machineguard.platform.alertincident.domain.queries.AlertIncidentQueries.GetIncidentHistoryQuery;
import com.machineguard.platform.alertincident.interfaces.rest.assemblers.AlertIncidentResourceAssembler;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.CorrectiveActionResource;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.IncidentDetailResource;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.IncidentResource;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.RegisterCorrectiveActionResource;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.ResolveIncidentResource;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ciclo de vida del incidente. Toda operacion queda limitada a la organizacion del JWT. */
@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents", description = "Incidentes ambientales, acciones correctivas y resolucion")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','VIEWER')")
public class IncidentController {

    private static final int MAX_LIMIT = 500;

    private final IncidentQueryService queries;
    private final RegisterCorrectiveActionCommandService correctiveActions;
    private final ResolveIncidentCommandService resolveIncident;
    private final AlertIncidentResourceAssembler assembler;

    public IncidentController(IncidentQueryService queries, RegisterCorrectiveActionCommandService correctiveActions,
                              ResolveIncidentCommandService resolveIncident, AlertIncidentResourceAssembler assembler) {
        this.queries = queries;
        this.correctiveActions = correctiveActions;
        this.resolveIncident = resolveIncident;
        this.assembler = assembler;
    }

    @GetMapping
    @Operation(summary = "Lista los incidentes de la organizacion, filtrables por zona, estado y periodo de apertura")
    public List<IncidentResource> list(
            @AuthenticationPrincipal IamPrincipal principal,
            @RequestParam(required = false) UUID monitoringZoneId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "100") int limit) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from debe ser anterior a to");
        }
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        return queries.handle(new GetIncidentHistoryQuery(principal.organizationId(), monitoringZoneId, status, from,
                to, bounded)).stream().map(assembler::incident).toList();
    }

    @GetMapping("/{incidentId}")
    @Operation(summary = "Detalle del incidente con su alerta, el reconocimiento y las acciones correctivas")
    public IncidentDetailResource detail(@AuthenticationPrincipal IamPrincipal principal,
                                         @PathVariable UUID incidentId) {
        return assembler.detail(queries.handle(new GetIncidentByIdQuery(principal.organizationId(), incidentId)));
    }

    @PostMapping("/{incidentId}/corrective-actions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registra la accion correctiva aplicada al incidente; el responsable es el usuario autenticado")
    public ResponseEntity<CorrectiveActionResource> registerCorrectiveAction(
            @AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID incidentId,
            @Valid @RequestBody RegisterCorrectiveActionResource body) {
        var action = correctiveActions.handle(new RegisterCorrectiveActionCommand(principal.organizationId(),
                incidentId, principal.userId(), body.description(), body.notes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.correctiveAction(action));
    }

    @PostMapping("/{incidentId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Resuelve el incidente con accion correctiva y rango seguro, y lo comunica a Traceability and Quality")
    public IncidentResource resolve(@AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID incidentId,
                                    @Valid @RequestBody(required = false) ResolveIncidentResource body) {
        String notes = body == null ? null : body.notes();
        return assembler.incident(resolveIncident.handle(
                new ResolveIncidentCommand(principal.organizationId(), incidentId, principal.userId(), notes)));
    }
}
