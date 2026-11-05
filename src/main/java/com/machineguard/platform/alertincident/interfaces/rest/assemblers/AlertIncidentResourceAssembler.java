package com.machineguard.platform.alertincident.interfaces.rest.assemblers;

import com.machineguard.platform.alertincident.application.commandservices.AcknowledgeAlertCommandService.Acknowledgement;
import com.machineguard.platform.alertincident.application.queryservices.IncidentQueryService.IncidentDetail;
import com.machineguard.platform.alertincident.domain.model.Alert;
import com.machineguard.platform.alertincident.domain.model.AlertSummary;
import com.machineguard.platform.alertincident.domain.model.CorrectiveAction;
import com.machineguard.platform.alertincident.domain.model.Incident;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.AcknowledgementResource;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.AlertAcknowledgementResource;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.AlertResource;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.CorrectiveActionResource;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.IncidentDetailResource;
import com.machineguard.platform.alertincident.interfaces.rest.resources.AlertIncidentResources.IncidentResource;
import org.springframework.stereotype.Component;

@Component
public class AlertIncidentResourceAssembler {

    public IncidentResource incident(Incident i) {
        return new IncidentResource(i.getId(), i.getOriginMeasurementId(), i.getMonitoringZoneId(),
                i.getEnvironmentalVariable() == null ? null : i.getEnvironmentalVariable().name(),
                i.getStatus().name(), i.getOpenedAt(), i.getResolvedAt(), i.getResolvedBy(), i.getResolutionNotes(),i.isHistorical());
    }

    public AlertResource alert(Alert a, Incident incident) {
        return new AlertResource(a.getId(), a.getIncidentId(), incident.getMonitoringZoneId(), a.getSeverity().name(),
                a.getStatus().name(), a.getMessage(), a.getRaisedAt(), a.getAcknowledgedAt(), a.getEscalatedAt(),incident.isHistorical());
    }

    public AlertResource alert(AlertSummary a) {
        return new AlertResource(a.id(), a.incidentId(), a.monitoringZoneId(), a.severity().name(),
                a.status().name(), a.message(), a.raisedAt(), a.acknowledgedAt(), a.escalatedAt(),a.historical());
    }

    public CorrectiveActionResource correctiveAction(CorrectiveAction c) {
        return new CorrectiveActionResource(c.getId(), c.getIncidentId(), c.getDescription(), c.getPerformedBy(),
                c.getPerformedAt(), c.getNotes());
    }

    public IncidentDetailResource detail(IncidentDetail detail, boolean administrator) {
        Alert alert = detail.alert();
        AcknowledgementResource acknowledgement = alert != null && alert.isAcknowledged()
                ? new AcknowledgementResource(alert.getAcknowledgedBy(), alert.getAcknowledgedAt(),
                alert.getAcknowledgementNote())
                : null;
        var reasons=new java.util.ArrayList<>(detail.resolutionBlockReasons());
        if (!administrator) reasons.add("READ_ONLY");
        if (detail.incident().isResolved()) reasons.add("INCIDENT_RESOLVED");
        if (detail.correctiveActions().isEmpty()) reasons.add("CORRECTIVE_ACTION_REQUIRED");
        return new IncidentDetailResource(incident(detail.incident()),
                alert == null ? null : alert(alert, detail.incident()), acknowledgement,
                detail.correctiveActions().stream().map(this::correctiveAction).toList(), detail.withinSafeRange(),
                administrator && !detail.incident().isResolved() && detail.withinSafeRange()
                        && !detail.correctiveActions().isEmpty(),reasons);
    }

    public AlertAcknowledgementResource acknowledgement(Acknowledgement result) {
        Alert alert = result.alert();
        return new AlertAcknowledgementResource(alert.getId(), alert.getIncidentId(), alert.getAcknowledgedBy(),
                alert.getAcknowledgedAt(), alert.getAcknowledgementNote(), alert.getStatus().name(),
                result.incident().getStatus().name());
    }
}
