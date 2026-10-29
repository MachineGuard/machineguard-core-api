package com.machineguard.platform.alertincident.domain.services;

import com.machineguard.platform.alertincident.domain.exceptions.AlertIncidentRuleException;
import com.machineguard.platform.alertincident.domain.model.CorrectiveAction;
import com.machineguard.platform.alertincident.domain.model.Incident;
import org.springframework.stereotype.Component;

import java.util.List;

/** Un incidente solo puede resolverse si existe al menos una accion correctiva registrada. */
@Component
public class IncidentResolutionService {

    private static final int MAX_SUMMARY_LENGTH = 1000;

    public void ensureResolvable(Incident incident, List<CorrectiveAction> actions) {
        incident.requireUnresolved("resolverlo");
        if (actions.isEmpty()) {
            throw new AlertIncidentRuleException(
                    "El incidente " + incident.getId() + " no puede resolverse sin una accion correctiva registrada");
        }
    }

    /** Resumen de las acciones aplicadas, en el texto que espera Traceability and Quality. */
    public String summarize(List<CorrectiveAction> actions) {
        String summary = String.join(" | ", actions.stream().map(CorrectiveAction::getDescription).toList());
        return summary.length() > MAX_SUMMARY_LENGTH ? summary.substring(0, MAX_SUMMARY_LENGTH) : summary;
    }
}
