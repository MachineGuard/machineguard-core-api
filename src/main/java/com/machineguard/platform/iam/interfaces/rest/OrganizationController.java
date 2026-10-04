package com.machineguard.platform.iam.interfaces.rest;

import com.machineguard.platform.iam.application.IamService;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.machineguard.platform.iam.interfaces.rest.IamResources.OrganizationResource;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Tenant organization queries")
@SecurityRequirement(name = "bearerAuth")
public class OrganizationController {
    private final IamService iam;
    public OrganizationController(IamService iam) { this.iam = iam; }

    @GetMapping("/{organizationId}")
    @Operation(summary = "Get the authenticated tenant's organization")
    public OrganizationResource get(@AuthenticationPrincipal IamPrincipal principal, @PathVariable UUID organizationId) {
        return iam.organization(principal, organizationId);
    }
}
