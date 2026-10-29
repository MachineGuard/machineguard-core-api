package com.machineguard.platform.alertincidentmanagement;

import com.machineguard.platform.alertincidentmanagement.domain.model.*;
import com.machineguard.platform.alertincidentmanagement.domain.queries.AlertQueries.*;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AlertDomainTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-28T10:00:00-05:00");
    private static final UUID INCIDENT = UUID.randomUUID();

    @Test void raisesPendingAlertWithExplicitSeverityAndOriginalTime() {
        for (AlertSeverity severity : AlertSeverity.values()) {
            Alert alert = Alert.raise(INCIDENT, severity, "Temperature outside safe range", NOW);
            assertNull(alert.getId());
            assertEquals(INCIDENT, alert.getIncidentId());
            assertEquals(severity, alert.getSeverity());
            assertEquals(AlertStatus.PENDING, alert.getStatus());
            assertEquals("Temperature outside safe range", alert.getMessage());
            assertEquals(NOW, alert.getRaisedAt());
            assertNull(alert.getAcknowledgedAt());
            assertNull(alert.getEscalatedAt());
        }
    }

    @Test void requiresIncidentSeverityMessageAndTime() {
        assertThrows(NullPointerException.class, () -> Alert.raise(null, AlertSeverity.HIGH, "Deviation", NOW));
        assertThrows(NullPointerException.class, () -> Alert.raise(INCIDENT, null, "Deviation", NOW));
        assertThrows(NullPointerException.class, () -> Alert.raise(INCIDENT, AlertSeverity.HIGH, null, NOW));
        assertThrows(NullPointerException.class, () -> Alert.raise(INCIDENT, AlertSeverity.HIGH, "Deviation", null));
    }

    @Test void rejectsBlankAndOversizedMessagesAndAcceptsMaximumLength() {
        for (String message : new String[] {"", " \t\n", "x".repeat(501)})
            assertThrows(IllegalArgumentException.class, () -> Alert.raise(INCIDENT, AlertSeverity.LOW, message, NOW));
        assertEquals(500, Alert.raise(INCIDENT, AlertSeverity.LOW, "x".repeat(500), NOW).getMessage().length());
    }

    @Test void queriesRequireTenantAndDetailIdentifier() {
        assertThrows(NullPointerException.class, () -> new GetAlerts(null, null, null));
        assertThrows(NullPointerException.class, () -> new GetAlertById(null, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> new GetAlertById(UUID.randomUUID(), null));
        assertDoesNotThrow(() -> new GetAlerts(UUID.randomUUID(), null, null));
    }
}
