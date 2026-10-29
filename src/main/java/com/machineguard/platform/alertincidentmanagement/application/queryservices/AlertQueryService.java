package com.machineguard.platform.alertincidentmanagement.application.queryservices;

import com.machineguard.platform.alertincidentmanagement.domain.exceptions.AlertNotFoundException;
import com.machineguard.platform.alertincidentmanagement.domain.model.Alert;
import com.machineguard.platform.alertincidentmanagement.domain.queries.AlertQueries.*;
import com.machineguard.platform.alertincidentmanagement.domain.repositories.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AlertQueryService {
    private final AlertRepository alerts;

    public AlertQueryService(AlertRepository alerts) { this.alerts = alerts; }

    public List<Alert> handle(GetAlerts query) { return alerts.findAlerts(query); }

    public Alert handle(GetAlertById query) {
        return alerts.findByIdAndOrganizationId(query.alertId(), query.organizationId())
                .orElseThrow(() -> new AlertNotFoundException("Alert not found"));
    }
}
