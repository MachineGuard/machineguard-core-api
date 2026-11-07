package com.machineguard.platform.customeracquisition.interfaces.rest;

import com.machineguard.platform.customeracquisition.application.PilotRequestService;
import com.machineguard.platform.customeracquisition.domain.model.PilotRequest;
import com.machineguard.platform.customeracquisition.domain.model.PilotRequestStatus;
import com.machineguard.platform.customeracquisition.interfaces.rest.PilotRequestResources.ApprovalResource;
import com.machineguard.platform.customeracquisition.interfaces.rest.PilotRequestResources.PilotRequestResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Operator routes use X-Operator-Key (see IAM OperatorKeyFilter). They are not user roles and carry no tenant.
 * Disabled (404) when IAM_OPERATOR_KEY_HASH is not configured.
 */
@RestController
@RequestMapping("/api/v1/operator/pilot-requests")
@Tag(name = "Operator pilot requests", description = "Global pilot approval with X-Operator-Key; not a user role")
public class OperatorPilotRequestController {
    static final String ACCEPT_PATH = "/api/v1/auth/invitations/accept";
    private final PilotRequestService requests;

    public OperatorPilotRequestController(PilotRequestService requests) { this.requests = requests; }

    @GetMapping
    @Operation(summary = "List pilot requests by status, oldest first (default PENDING, at most 200)")
    public List<PilotRequestResource> list(@RequestParam(defaultValue = "PENDING") PilotRequestStatus status) {
        return requests.list(status).stream().map(OperatorPilotRequestController::resource).toList();
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve once. The activation token is returned only by the first approval; repeats do not create a second organization.")
    public ResponseEntity<ApprovalResource> approve(@PathVariable UUID id) {
        var decision = requests.approve(id);
        var request = decision.request();
        var invitation = decision.invitation();
        var body = new ApprovalResource(request.id(), request.status(), request.organizationId(), invitation.isPresent(),
                invitation.map(i -> i.invitationToken()).orElse(null),
                invitation.map(i -> i.expiresAt()).orElse(null), ACCEPT_PATH);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending request. Idempotent for rejected requests; approved requests conflict.")
    public PilotRequestResource reject(@PathVariable UUID id) {
        return resource(requests.reject(id));
    }

    static PilotRequestResource resource(PilotRequest r) {
        return new PilotRequestResource(r.id(), r.companyName(), r.contactName(), r.contactEmail(), r.contactPhone(),
                r.industry(), r.estimatedMonthlyLoss(), r.status(), r.organizationId(), r.createdAt(), r.decidedAt());
    }
}
