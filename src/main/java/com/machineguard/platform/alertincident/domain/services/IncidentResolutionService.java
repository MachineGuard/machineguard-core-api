package com.machineguard.platform.alertincident.domain.services;

import com.machineguard.platform.alertincident.domain.exceptions.AlertIncidentRuleException;
import com.machineguard.platform.alertincident.domain.model.CorrectiveAction;
import com.machineguard.platform.alertincident.domain.model.Incident;
import org.springframework.stereotype.Component;

import java.util.List;

/** Formal resolution requires corrective evidence and a recovered environmental condition. */
@Component
public class IncidentResolutionService {

    private static final int MAX_SUMMARY_LENGTH = 1000;

    public void ensureResolvable(Incident incident, List<CorrectiveAction> actions, boolean withinSafeRange) {
        incident.requireUnresolved("resolverlo");
        if (actions.isEmpty()) {
            throw new AlertIncidentRuleException(
                    "El incidente " + incident.getId() + " no puede resolverse sin una accion correctiva registrada");
        }
        if (!withinSafeRange) {
            throw new AlertIncidentRuleException("La zona debe volver al rango seguro antes de resolver el incidente");
        }
    }

    /** Resumen de las acciones aplicadas, en el texto que espera Traceability and Quality. */
    public String summarize(List<CorrectiveAction> actions) {
        String summary = String.join(" | ", actions.stream().map(CorrectiveAction::getDescription).toList());
        return summary.length() > MAX_SUMMARY_LENGTH ? summary.substring(0, MAX_SUMMARY_LENGTH) : summary;
    }
}
