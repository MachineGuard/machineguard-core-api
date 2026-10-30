package com.machineguard.platform.alertincident;

import com.machineguard.platform.alertincident.domain.exceptions.AlertIncidentRuleException;
import com.machineguard.platform.alertincident.domain.model.Alert;
import com.machineguard.platform.alertincident.domain.model.AlertSeverity;
import com.machineguard.platform.alertincident.domain.model.AlertStatus;
import com.machineguard.platform.alertincident.domain.model.CorrectiveAction;
import com.machineguard.platform.alertincident.domain.model.EnvironmentalVariable;
import com.machineguard.platform.alertincident.domain.model.Incident;
import com.machineguard.platform.alertincident.domain.model.IncidentStatus;
import com.machineguard.platform.alertincident.domain.services.AlertPolicyService;
import com.machineguard.platform.alertincident.domain.services.IncidentResolutionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertIncidentDomainTest {

    private static final Instant T0 = Instant.parse("2026-10-20T10:00:00Z");
    private static final UUID ORG = id(1);
    private static final UUID ZONE = id(2);
    private static final UUID USER = id(3);

    private static UUID id(long n) {
        return new UUID(0, n);
    }

    private Incident openIncident() {
        return Incident.open(id(10), ORG, ZONE, EnvironmentalVariable.TEMPERATURE, T0);
    }

    private CorrectiveAction action(Incident incident) {
        return CorrectiveAction.register(incident.getId() == null ? id(99) : incident.getId(),
                "Se reemplazo el compresor", USER, null, T0);
    }

    @Test
    void severityGrowsWithTheDistanceFromTheViolatedLimit() {
        AlertPolicyService policy = new AlertPolicyService();
        BigDecimal min = new BigDecimal("2");
        BigDecimal max = new BigDecimal("8");
        assertEquals(AlertSeverity.LOW, policy.classify(new BigDecimal("9.5"), min, max));
        assertEquals(AlertSeverity.MEDIUM, policy.classify(new BigDecimal("11"), min, max));
        assertEquals(AlertSeverity.HIGH, policy.classify(new BigDecimal("15"), min, max));
        assertEquals(AlertSeverity.CRITICAL, policy.classify(new BigDecimal("30"), min, max));
        // por debajo del minimo se mide contra el minimo
        assertEquals(AlertSeverity.MEDIUM, policy.classify(new BigDecimal("-2"), min, max));
        assertTrue(AlertSeverity.CRITICAL.isCritical());
        assertTrue(AlertSeverity.HIGH.isHigh());
    }

    @Test
    void incidentStartsOpenWithAnOpenKeyAndResolvesOnlyOnce() {
        Incident incident = openIncident();
        assertEquals(IncidentStatus.OPEN, incident.getStatus());
        assertFalse(incident.isResolved());

        incident.acknowledge();
        assertEquals(IncidentStatus.ACKNOWLEDGED, incident.getStatus());

        incident.resolve(USER, "  Camara estabilizada  ", T0.plusSeconds(600));
        assertTrue(incident.isResolved());
        assertEquals("Camara estabilizada", incident.getResolutionNotes());
        assertEquals(USER, incident.getResolvedBy());
        assertEquals(T0.plusSeconds(600), incident.getResolvedAt());

        assertThrows(AlertIncidentRuleException.class, () -> incident.resolve(USER, null, T0.plusSeconds(700)));
        assertThrows(AlertIncidentRuleException.class, incident::acknowledge);
    }

    @Test
    void blankResolutionNotesAreStoredAsNull() {
        Incident incident = openIncident();
        incident.resolve(USER, "   ", T0.plusSeconds(60));
        assertNull(incident.getResolutionNotes());
    }

    @Test
    void alertIsAcknowledgedOnceAndNeverAfterBeingClosed() {
        Alert alert = Alert.raise(id(10), AlertSeverity.HIGH, "Temperatura fuera de rango", T0);
        assertEquals(AlertStatus.PENDING, alert.getStatus());
        assertFalse(alert.isAcknowledged());

        alert.acknowledge(USER, "  voy en camino ", T0.plusSeconds(30));
        assertEquals(AlertStatus.ACKNOWLEDGED, alert.getStatus());
        assertEquals(USER, alert.getAcknowledgedBy());
        assertEquals("voy en camino", alert.getAcknowledgementNote());
        assertThrows(AlertIncidentRuleException.class, () -> alert.acknowledge(USER, null, T0.plusSeconds(40)));

        alert.close(T0.plusSeconds(100));
        assertEquals(AlertStatus.CLOSED, alert.getStatus());

        Alert other = Alert.raise(id(11), AlertSeverity.LOW, "x", T0);
        other.close(T0.plusSeconds(5));
        assertThrows(AlertIncidentRuleException.class, () -> other.acknowledge(USER, null, T0.plusSeconds(6)));
    }

    @Test
    void alertMessageIsTruncatedToTheColumnLength() {
        Alert alert = Alert.raise(id(10), AlertSeverity.LOW, "x".repeat(900), T0);
        assertEquals(500, alert.getMessage().length());
    }

    @Test
    void correctiveActionRequiresADescription() {
        assertThrows(IllegalArgumentException.class, () -> CorrectiveAction.register(id(10), "  ", USER, null, T0));
        CorrectiveAction action = CorrectiveAction.register(id(10), " Se limpio el filtro ", USER, "  ", T0);
        assertEquals("Se limpio el filtro", action.getDescription());
        assertNull(action.getNotes());
        action.updateNotes("revisar en 24 h");
        assertEquals("revisar en 24 h", action.getNotes());
    }

    @Test
    void incidentCannotBeResolvedWithoutACorrectiveAction() {
        IncidentResolutionService resolution = new IncidentResolutionService();
        Incident incident = openIncident();
        assertThrows(AlertIncidentRuleException.class, () -> resolution.ensureResolvable(incident, List.of()));
        resolution.ensureResolvable(incident, List.of(action(incident)));
    }

    @Test
    void resolutionSummaryJoinsActionsAndRespectsTheMaximumLength() {
        IncidentResolutionService resolution = new IncidentResolutionService();
        Incident incident = openIncident();
        CorrectiveAction first = action(incident);
        CorrectiveAction second = CorrectiveAction.register(incident.getId() == null ? id(99) : incident.getId(),
                "Se recalibro el sensor", USER, null, T0);
        assertEquals("Se reemplazo el compresor | Se recalibro el sensor", resolution.summarize(List.of(first, second)));

        CorrectiveAction longAction = CorrectiveAction.register(id(99), "y".repeat(1000), USER, null, T0);
        assertEquals(1000, resolution.summarize(List.of(longAction, longAction)).length());
    }

    @Test
    void openKeyIdentifiesOrganizationZoneAndVariable() {
        assertEquals(ORG + ":" + ZONE + ":TEMPERATURE",
                Incident.openKeyOf(ORG, ZONE, EnvironmentalVariable.TEMPERATURE));
    }
}
