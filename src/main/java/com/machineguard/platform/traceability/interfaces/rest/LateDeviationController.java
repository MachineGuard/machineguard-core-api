package com.machineguard.platform.traceability.interfaces.rest;

import com.machineguard.platform.iam.infrastructure.security.IamPrincipal;
import com.machineguard.platform.traceability.application.LateDeviationService;
import com.machineguard.platform.traceability.domain.model.LateDeviation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/traceability/late-deviations")
@SecurityRequirement(name="bearerAuth")
public class LateDeviationController {
    private final LateDeviationService records;
    public LateDeviationController(LateDeviationService records){this.records=records;}
    public record Resource(UUID measurementId,UUID monitoringZoneId,UUID monitoringPointId,UUID sensorNodeId,
            String environmentalVariable,BigDecimal measuredValue,BigDecimal minimumValue,BigDecimal maximumValue,
            Instant recordedAt,Instant receivedAt) {
        static Resource from(LateDeviation row){return new Resource(row.getMeasurementId(),row.getMonitoringZoneId(),
                row.getMonitoringPointId(),row.getSensorNodeId(),row.getVariable().name(),row.getMeasuredValue(),
                row.getMinimumValue(),row.getMaximumValue(),row.getRecordedAt(),row.getReceivedAt());}
    }
    @GetMapping
    public List<Resource> list(@AuthenticationPrincipal IamPrincipal principal,@RequestParam UUID monitoringZoneId,
                              @RequestParam(defaultValue="100") int limit) {
        return records.find(principal.organizationId(),monitoringZoneId,limit).stream().map(Resource::from).toList();
    }
}
