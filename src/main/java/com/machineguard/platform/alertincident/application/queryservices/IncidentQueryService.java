package com.machineguard.platform.alertincident.application.queryservices;

import com.machineguard.platform.alertincident.domain.exceptions.AlertIncidentNotFoundException;
import com.machineguard.platform.alertincident.domain.model.Alert;
import com.machineguard.platform.alertincident.domain.model.CorrectiveAction;
import com.machineguard.platform.alertincident.domain.model.Incident;
import com.machineguard.platform.alertincident.domain.queries.AlertIncidentQueries.GetIncidentByIdQuery;
import com.machineguard.platform.alertincident.domain.queries.AlertIncidentQueries.GetIncidentHistoryQuery;
import com.machineguard.platform.alertincident.domain.repositories.AlertRepository;
import com.machineguard.platform.alertincident.domain.repositories.CorrectiveActionRepository;
import com.machineguard.platform.alertincident.domain.repositories.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class IncidentQueryService {

    private final IncidentRepository incidents;
    private final AlertRepository alerts;
    private final CorrectiveActionRepository actions;

    public IncidentQueryService(IncidentRepository incidents, AlertRepository alerts, CorrectiveActionRepository actions) {
        this.incidents = incidents;
        this.alerts = alerts;
        this.actions = actions;
    }

    /** Incidente con su alerta (reconocimiento incluido) y las acciones correctivas registradas. */
    public record IncidentDetail(Incident incident, Alert alert, List<CorrectiveAction> correctiveActions) {
    }

    public List<Incident> handle(GetIncidentHistoryQuery query) {
        return incidents.search(query.organizationId(), query.monitoringZoneId(), query.status(), query.from(),
                query.to(), query.limit());
    }

    public IncidentDetail handle(GetIncidentByIdQuery query) {
        Incident incident = incidents.findByIdAndOrganizationId(query.incidentId(), query.organizationId())
                .orElseThrow(() -> new AlertIncidentNotFoundException("Incidente " + query.incidentId() + " no encontrado"));
        return new IncidentDetail(incident, alerts.findByIncidentId(incident.getId()).orElse(null),
                actions.findByIncidentId(incident.getId()));
    }
}
