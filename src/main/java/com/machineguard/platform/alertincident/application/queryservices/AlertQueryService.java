package com.machineguard.platform.alertincident.application.queryservices;

import com.machineguard.platform.alertincident.domain.exceptions.AlertIncidentNotFoundException;
import com.machineguard.platform.alertincident.domain.model.AlertSummary;
import com.machineguard.platform.alertincident.domain.queries.AlertQueries.*;
import com.machineguard.platform.alertincident.domain.repositories.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AlertQueryService {
    private final AlertRepository alerts;

    public AlertQueryService(AlertRepository alerts) { this.alerts = alerts; }

    public List<AlertSummary> handle(GetAlerts query) { return alerts.findAlerts(query); }

    public AlertSummary handle(GetAlertById query) {
        return alerts.findSummaryByIdAndOrganizationId(query.alertId(), query.organizationId())
                .orElseThrow(() -> new AlertIncidentNotFoundException("Alert not found"));
    }
}
