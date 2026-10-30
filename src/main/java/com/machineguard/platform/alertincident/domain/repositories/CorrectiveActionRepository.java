package com.machineguard.platform.alertincident.domain.repositories;

import com.machineguard.platform.alertincident.domain.model.CorrectiveAction;

import java.util.List;
import java.util.UUID;

public interface CorrectiveActionRepository {

    CorrectiveAction save(CorrectiveAction action);

    List<CorrectiveAction> findByIncidentId(UUID incidentId);
}
