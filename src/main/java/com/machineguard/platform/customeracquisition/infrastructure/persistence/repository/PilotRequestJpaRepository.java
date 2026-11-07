package com.machineguard.platform.customeracquisition.infrastructure.persistence.repository;

import com.machineguard.platform.customeracquisition.domain.model.PilotRequestStatus;
import com.machineguard.platform.customeracquisition.infrastructure.persistence.PilotRequestEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PilotRequestJpaRepository extends JpaRepository<PilotRequestEntity, UUID> {
    Optional<PilotRequestEntity> findByPendingContactEmail(String pendingContactEmail);

    List<PilotRequestEntity> findTop200ByStatusOrderByCreatedAtAsc(PilotRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PilotRequestEntity p where p.id = :id")
    Optional<PilotRequestEntity> findByIdForUpdate(@Param("id") UUID id);
}
