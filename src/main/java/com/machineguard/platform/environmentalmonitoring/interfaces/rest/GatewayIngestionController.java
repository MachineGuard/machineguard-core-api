package com.machineguard.platform.environmentalmonitoring.interfaces.rest;

import com.machineguard.platform.environmentalmonitoring.application.commandservices.IngestGatewayReadingsCommandService;
import com.machineguard.platform.environmentalmonitoring.application.commandservices.IngestGatewayReadingsCommandService.*;
import com.machineguard.platform.iam.infrastructure.security.GatewayPrincipal;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/measurements")
@Tag(name="Gateway ingestion")
@SecurityScheme(name="gatewayCredential",type=SecuritySchemeType.APIKEY,in=SecuritySchemeIn.HEADER,paramName="X-Device-Credential")
@SecurityRequirement(name="gatewayCredential")
@PreAuthorize("hasAuthority('GATEWAY_INGEST')")
public class GatewayIngestionController {
    private final IngestGatewayReadingsCommandService commands;
    public GatewayIngestionController(IngestGatewayReadingsCommandService commands) { this.commands=commands; }
    public record ReadingResource(@NotBlank @Size(max=100) String deviceCode,
            @NotNull @Digits(integer=4,fraction=2) BigDecimal temperature,
            @NotNull @Digits(integer=4,fraction=2) BigDecimal humidity,@NotNull OffsetDateTime capturedAt) {
        Reading command() { return new Reading(deviceCode,temperature,humidity,capturedAt); }
    }
    public record BatchResource(@NotEmpty @Size(max=500) List<@Valid ReadingResource> measurements) { }
    @PostMapping
    public ResponseEntity<Receipt> one(@AuthenticationPrincipal GatewayPrincipal gateway,@Valid @RequestBody ReadingResource body) {
        return ingest(gateway,List.of(body.command()));
    }
    @PostMapping("/batch")
    public ResponseEntity<Receipt> batch(@AuthenticationPrincipal GatewayPrincipal gateway,@Valid @RequestBody BatchResource body) {
        return ingest(gateway,body.measurements().stream().map(ReadingResource::command).toList());
    }
    private ResponseEntity<Receipt> ingest(GatewayPrincipal gateway,List<Reading> readings) {
        Receipt receipt=commands.handle(new Command(gateway.organizationId(),gateway.sensorNodeIds(),readings));
        return ResponseEntity.status(receipt.measurementsCreated()>0?201:200).body(receipt);
    }
}
