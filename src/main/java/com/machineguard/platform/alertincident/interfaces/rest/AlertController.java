package com.machineguard.platform.alertincident.interfaces.rest;

import com.machineguard.platform.alertincident.application.queryservices.AlertQueryService;
import com.machineguard.platform.alertincident.domain.model.AlertStatus;
import com.machineguard.platform.alertincident.domain.queries.AlertQueries.*;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.AlertResource;
import com.machineguard.platform.alertincident.interfaces.rest.assemblers.AlertIncidentResourceAssembler;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','VIEWER')")
public class AlertController {
    private final AlertQueryService queries;
    private final AlertIncidentResourceAssembler assembler;

    public AlertController(AlertQueryService queries, AlertIncidentResourceAssembler assembler) {
        this.queries = queries;
        this.assembler = assembler;
    }

    @GetMapping
    public List<AlertResource> list(@AuthenticationPrincipal IamPrincipal principal,
            @RequestParam(required = false) UUID monitoringZoneId, @RequestParam(required = false) AlertStatus status) {
        return queries.handle(new GetAlerts(principal.organizationId(), monitoringZoneId, status))
                .stream().map(assembler::alert).toList();
    }

    @GetMapping("/{alertId}")
    public AlertResource detail(@AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID alertId) {
        return assembler.alert(queries.handle(new GetAlertById(principal.organizationId(), alertId)));
    }
}
