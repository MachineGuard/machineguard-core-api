package com.machineguard.platform.alertincident.interfaces.rest;

import com.machineguard.platform.alertincident.application.commandservices.AcknowledgeAlertCommandService;
import com.machineguard.platform.alertincident.domain.commands.AlertIncidentCommands.AcknowledgeAlertCommand;
import com.machineguard.platform.alertincident.interfaces.rest.assemblers.AlertIncidentResourceAssembler;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.AcknowledgeAlertResource;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.AlertAcknowledgementResource;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Reconocimiento de alertas. La consulta de alertas (GET) vive en el controlador de alertas. */
@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alert acknowledgements", description = "Reconocimiento de alertas por un responsable")
@SecurityRequirement(name = "bearerAuth")
public class AlertAcknowledgementController {

    private final AcknowledgeAlertCommandService acknowledge;
    private final AlertIncidentResourceAssembler assembler;

    public AlertAcknowledgementController(AcknowledgeAlertCommandService acknowledge,
                                          AlertIncidentResourceAssembler assembler) {
        this.acknowledge = acknowledge;
        this.assembler = assembler;
    }

    @PostMapping("/{alertId}/acknowledgements")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reconoce la alerta; el responsable es el usuario autenticado y el incidente pasa a ACKNOWLEDGED")
    public ResponseEntity<AlertAcknowledgementResource> acknowledge(
            @AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID alertId,
            @Valid @RequestBody(required = false) AcknowledgeAlertResource body) {
        String note = body == null ? null : body.note();
        var result = acknowledge.handle(
                new AcknowledgeAlertCommand(principal.organizationId(), alertId, principal.userId(), note));
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.acknowledgement(result));
    }
}
