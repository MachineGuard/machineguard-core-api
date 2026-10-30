package com.machineguard.platform.iam.interfaces.rest;

import com.machineguard.platform.iam.application.GatewayCredentialService;
import com.machineguard.platform.iam.domain.model.Gateway;
import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/gateways")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name="bearerAuth")
@Tag(name="Gateway credentials")
public class GatewayController {
    private final GatewayCredentialService gateways;
    public GatewayController(GatewayCredentialService gateways) { this.gateways=gateways; }
    public record RegisterGateway(@NotBlank @Size(max=150) String name,
            @NotEmpty @Size(max=100) Set<@NotNull UUID> sensorNodeIds, @Min(1) @Max(90) Integer credentialTtlDays) { }
    public record RotateGateway(@Min(1) @Max(90) Integer credentialTtlDays) { }
    public record GatewayResource(UUID id,String name,Set<UUID> sensorNodeIds,UUID createdBy,UUID updatedBy,
            Instant createdAt,Instant updatedAt,Instant expiresAt,Instant revokedAt) { }
    public record GatewayCredentialResource(GatewayResource gateway,String credential) { }
    private GatewayResource resource(Gateway g) {
        return new GatewayResource(g.id(),g.name(),g.sensorNodeIds(),g.createdBy(),g.updatedBy(),g.createdAt(),g.updatedAt(),g.expiresAt(),g.revokedAt());
    }
    @PostMapping
    public ResponseEntity<GatewayCredentialResource> register(@AuthenticationPrincipal IamPrincipal user,
            @Valid @RequestBody RegisterGateway body) {
        var issued=gateways.register(user.organizationId(),user.userId(),body.name(),body.sensorNodeIds(),body.credentialTtlDays());
        return ResponseEntity.created(URI.create("/api/v1/gateways/"+issued.gateway().id())).header("Cache-Control","no-store")
                .body(new GatewayCredentialResource(resource(issued.gateway()),issued.credential()));
    }
    @GetMapping public List<GatewayResource> list(@AuthenticationPrincipal IamPrincipal user) {
        return gateways.list(user.organizationId(),user.userId()).stream().map(this::resource).toList();
    }
    @GetMapping("/{id}") public GatewayResource detail(@AuthenticationPrincipal IamPrincipal user,@PathVariable UUID id) {
        return resource(gateways.get(user.organizationId(),user.userId(),id));
    }
    @PostMapping("/{id}/credentials")
    public ResponseEntity<GatewayCredentialResource> rotate(@AuthenticationPrincipal IamPrincipal user,@PathVariable UUID id,
            @Valid @RequestBody(required=false) RotateGateway body) {
        var issued=gateways.rotate(user.organizationId(),user.userId(),id,body==null?null:body.credentialTtlDays());
        return ResponseEntity.ok().header("Cache-Control","no-store")
                .body(new GatewayCredentialResource(resource(issued.gateway()),issued.credential()));
    }
    @PostMapping("/{id}/revoke") public GatewayResource revoke(@AuthenticationPrincipal IamPrincipal user,@PathVariable UUID id) {
        return resource(gateways.revoke(user.organizationId(),user.userId(),id));
    }
}
