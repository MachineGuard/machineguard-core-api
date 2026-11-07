package com.machineguard.platform.customeracquisition.interfaces.rest;

import com.machineguard.platform.customeracquisition.application.PilotRequestData;
import com.machineguard.platform.customeracquisition.application.PilotRequestRateLimiter;
import com.machineguard.platform.customeracquisition.application.PilotRequestService;
import com.machineguard.platform.customeracquisition.application.PilotRequestThrottledException;
import com.machineguard.platform.customeracquisition.domain.model.PilotRequestStatus;
import com.machineguard.platform.customeracquisition.interfaces.rest.PilotRequestResources.PilotRequestAccepted;
import com.machineguard.platform.customeracquisition.interfaces.rest.PilotRequestResources.PilotRequestSubmission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pilot-requests")
@Tag(name = "Pilot requests", description = "Public intake from the Landing; approval is manual by an operator")
public class PilotRequestController {
    private final PilotRequestService requests;
    private final PilotRequestRateLimiter limiter;

    public PilotRequestController(PilotRequestService requests, PilotRequestRateLimiter limiter) {
        this.requests = requests;
        this.limiter = limiter;
    }

    @PostMapping
    @Operation(summary = "Submit a pilot request. Creates no tenant; returns 202 with the pending request id.")
    public ResponseEntity<PilotRequestAccepted> submit(@Valid @RequestBody PilotRequestSubmission body,
                                                       HttpServletRequest client) {
        long retry = limiter.attempt(client.getRemoteAddr());
        if (retry > 0) throw new PilotRequestThrottledException(retry);
        var id = requests.submit(new PilotRequestData(body.companyName(), body.contactName(), body.contactEmail(),
                body.contactPhone(), body.industry(), body.estimatedMonthlyLoss()));
        return ResponseEntity.accepted().header("Cache-Control", "no-store")
                .body(new PilotRequestAccepted(id, PilotRequestStatus.PENDING));
    }
}
