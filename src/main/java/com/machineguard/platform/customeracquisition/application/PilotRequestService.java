package com.machineguard.platform.customeracquisition.application;

import com.machineguard.platform.customeracquisition.domain.model.PilotRequest;
import com.machineguard.platform.customeracquisition.domain.model.PilotRequestStatus;
import com.machineguard.platform.customeracquisition.domain.port.PilotRequestRepository;
import com.machineguard.platform.iam.application.IamService;
import com.machineguard.platform.iam.application.integration.PilotRequestSubmitted;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PilotRequestService {
    private final PilotRequestRepository requests;
    private final IamService iam;
    private final Clock clock;

    public PilotRequestService(PilotRequestRepository requests, IamService iam, Clock clock) {
        this.requests = requests;
        this.iam = iam;
        this.clock = clock;
    }

    /**
     * Stores a pilot request without creating any tenant. A pending request for the same email is returned
     * unchanged, so its data is not updated by a repeated submission.
     */
    @Transactional
    public UUID submit(PilotRequestData data) {
        String email = PilotRequest.normalizeEmail(data.contactEmail());
        return requests.findPendingByContactEmail(email).map(PilotRequest::id).orElseGet(() -> requests.save(PilotRequest.submit(
                UUID.randomUUID(), data.companyName(), data.contactName(), email, data.contactPhone(),
                data.industry(), data.estimatedMonthlyLoss(), clock.instant())).id());
    }

    /**
     * Approves once: the existing IAM flow creates an ACTIVE organization, an INACTIVE administrator and a
     * one-time invitation. The raw token is returned only by the first approval.
     */
    @Transactional
    public Decision approve(UUID id) {
        PilotRequest request = requests.findByIdForUpdate(id).orElseThrow(() -> notFound());
        if (request.status() == PilotRequestStatus.APPROVED) return new Decision(request, Optional.empty());
        if (request.status() == PilotRequestStatus.REJECTED) throw conflict("Pilot request was already rejected");
        var invitation = iam.handlePilotRequest(new PilotRequestSubmitted(request.id(), request.companyName(),
                request.contactName(), request.contactEmail()))
                .orElseThrow(() -> conflict("Pilot request is already provisioned"));
        request.approve(invitation.organizationId(), clock.instant());
        return new Decision(requests.save(request), Optional.of(invitation));
    }

    @Transactional
    public PilotRequest reject(UUID id) {
        PilotRequest request = requests.findByIdForUpdate(id).orElseThrow(() -> notFound());
        if (request.status() == PilotRequestStatus.REJECTED) return request;
        if (request.status() == PilotRequestStatus.APPROVED) throw conflict("Pilot request was already approved");
        request.reject(clock.instant());
        return requests.save(request);
    }

    @Transactional(readOnly = true)
    public List<PilotRequest> list(PilotRequestStatus status) {
        return requests.findOldestByStatus(status);
    }

    public record Decision(PilotRequest request, Optional<IamService.PilotInvitation> invitation) { }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Pilot request not found");
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
