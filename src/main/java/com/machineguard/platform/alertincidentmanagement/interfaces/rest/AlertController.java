package com.machineguard.platform.alertincidentmanagement.interfaces.rest;

import com.machineguard.platform.alertincidentmanagement.application.queryservices.AlertQueryService;
import com.machineguard.platform.alertincidentmanagement.domain.model.AlertStatus;
import com.machineguard.platform.alertincidentmanagement.domain.queries.AlertQueries.*;
import com.machineguard.platform.alertincidentmanagement.interfaces.rest.resources.AlertResources.AlertResource;
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

    public AlertController(AlertQueryService queries) { this.queries = queries; }

    @GetMapping
    public List<AlertResource> list(@AuthenticationPrincipal IamPrincipal principal,
            @RequestParam(required = false) UUID monitoringZoneId, @RequestParam(required = false) AlertStatus status) {
        return queries.handle(new GetAlerts(principal.organizationId(), monitoringZoneId, status))
                .stream().map(AlertResource::from).toList();
    }

    @GetMapping("/{alertId}")
    public AlertResource detail(@AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID alertId) {
        return AlertResource.from(queries.handle(new GetAlertById(principal.organizationId(), alertId)));
    }
}
