package com.machineguard.platform.customeracquisition.infrastructure.persistence.adapter;

import com.machineguard.platform.customeracquisition.domain.model.PilotRequest;
import com.machineguard.platform.customeracquisition.domain.model.PilotRequestStatus;
import com.machineguard.platform.customeracquisition.domain.port.PilotRequestRepository;
import com.machineguard.platform.customeracquisition.infrastructure.persistence.PilotRequestEntity;
import com.machineguard.platform.customeracquisition.infrastructure.persistence.repository.PilotRequestJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaPilotRequestRepositoryAdapter implements PilotRequestRepository {
    private final PilotRequestJpaRepository repository;

    public JpaPilotRequestRepositoryAdapter(PilotRequestJpaRepository repository) { this.repository = repository; }

    // Flush here so a duplicate pending email surfaces inside the calling use case.
    @Override public PilotRequest save(PilotRequest request) {
        return repository.saveAndFlush(new PilotRequestEntity(request)).toDomain();
    }

    @Override public Optional<PilotRequest> findById(UUID id) {
        return repository.findById(id).map(PilotRequestEntity::toDomain);
    }

    @Override public Optional<PilotRequest> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(PilotRequestEntity::toDomain);
    }

    @Override public Optional<PilotRequest> findPendingByContactEmail(String normalizedEmail) {
        return repository.findByPendingContactEmail(normalizedEmail).map(PilotRequestEntity::toDomain);
    }

    @Override public List<PilotRequest> findOldestByStatus(PilotRequestStatus status) {
        return repository.findTop200ByStatusOrderByCreatedAtAsc(status).stream().map(PilotRequestEntity::toDomain).toList();
    }
}
