package com.machineguard.platform.alertincident.domain.repositories;

import com.machineguard.platform.alertincident.domain.model.Alert;

import java.util.Optional;
import java.util.UUID;

public interface AlertRepository {

    Alert save(Alert alert);

    Optional<Alert> findById(UUID id);

    Optional<Alert> findByIncidentId(UUID incidentId);
}
