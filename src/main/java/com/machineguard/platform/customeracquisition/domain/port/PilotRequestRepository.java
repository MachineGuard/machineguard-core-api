package com.machineguard.platform.customeracquisition.domain.port;

import com.machineguard.platform.customeracquisition.domain.model.PilotRequest;
import com.machineguard.platform.customeracquisition.domain.model.PilotRequestStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PilotRequestRepository {
    PilotRequest save(PilotRequest request);
    Optional<PilotRequest> findById(UUID id);
    /** Locks the row for an operator decision so concurrent approvals serialize. */
    Optional<PilotRequest> findByIdForUpdate(UUID id);
    Optional<PilotRequest> findPendingByContactEmail(String normalizedEmail);
    /** Oldest first, at most 200 rows. */
    List<PilotRequest> findOldestByStatus(PilotRequestStatus status);
}
