package com.machineguard.platform.alertincident;

import com.machineguard.platform.alertincident.domain.model.*;
import com.machineguard.platform.alertincident.domain.queries.AlertQueries.GetAlerts;
import com.machineguard.platform.alertincident.domain.repositories.AlertRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:alertpersistence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}",
        "spring.jpa.hibernate.ddl-auto=none"})
class AlertPersistenceIntegrationTest {
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-28T10:00:00.123456-05:00");
    static final UUID ORG = UUID.randomUUID();
    static final UUID ZONE = UUID.randomUUID();
    @Autowired AlertRepository alerts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clean() {
        jdbc.update("DELETE FROM alerts");
        jdbc.update("DELETE FROM corrective_actions");
        jdbc.update("DELETE FROM incidents");
    }

    UUID incident(UUID organizationId, UUID zoneId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO incidents (id, origin_measurement_id, organization_id, monitoring_zone_id, status, opened_at) VALUES (?, ?, ?, ?, 'OPEN', ?)",
                id, UUID.randomUUID(), organizationId, zoneId, NOW);
        return id;
    }

    @Test void persistsAndReloadsEverySeverityWithUuidAndTimestamps() {
        for (AlertSeverity severity : AlertSeverity.values()) {
            UUID incidentId = incident(ORG, ZONE);
            Alert saved = alerts.save(Alert.raise(incidentId, severity, "Outside safe range", NOW.toInstant()));
            assertNotNull(saved.getId());
            Alert loaded = alerts.findByIdAndOrganizationId(saved.getId(), ORG).orElseThrow();
            assertEquals(incidentId, loaded.getIncidentId());
            assertEquals(severity, loaded.getSeverity());
            assertEquals("Outside safe range", loaded.getMessage());
            assertEquals(AlertStatus.PENDING, loaded.getStatus());
            assertEquals(NOW.toInstant(), loaded.getRaisedAt());
            assertNull(loaded.getAcknowledgedAt());
            assertNull(loaded.getEscalatedAt());
        }
    }

    @Test void databaseRejectsSecondAlertForTheSameIncident() {
        UUID id = incident(ORG, ZONE);
        alerts.save(Alert.raise(id, AlertSeverity.HIGH, "First", NOW.toInstant()));
        assertThrows(DataIntegrityViolationException.class,
                () -> alerts.save(Alert.raise(id, AlertSeverity.CRITICAL, "Duplicate", NOW.toInstant())));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM alerts", Integer.class));
    }

    @Test void databaseRejectsAlertWithoutAnExistingIncident() {
        assertThrows(DataIntegrityViolationException.class,
                () -> alerts.save(Alert.raise(UUID.randomUUID(), AlertSeverity.LOW, "Missing incident", NOW.toInstant())));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM alerts", Integer.class));
    }

    @Test void repositoryFiltersOrganizationBeforeReturningResults() {
        Alert own = alerts.save(Alert.raise(incident(ORG, ZONE), AlertSeverity.HIGH, "Own", NOW.toInstant()));
        UUID other = UUID.randomUUID();
        Alert foreign = alerts.save(Alert.raise(incident(other, ZONE), AlertSeverity.HIGH, "Foreign", NOW.toInstant()));
        assertEquals(List.of(own.getId()), alerts.findAlerts(new GetAlerts(ORG, ZONE, AlertStatus.PENDING))
                .stream().map(AlertSummary::id).toList());
        assertTrue(alerts.findByIdAndOrganizationId(foreign.getId(), ORG).isEmpty());
        assertThrows(NullPointerException.class, () -> alerts.findByIdAndOrganizationId(own.getId(), null));
    }

    @Test void readsExistingLifecycleStatesAndOptionalTimesWithoutImplementingTransitions() {
        for (AlertStatus status : AlertStatus.values()) {
            Alert saved = alerts.save(Alert.raise(incident(ORG, ZONE), AlertSeverity.MEDIUM, status.name(), NOW.toInstant()));
            // Fixtures stand for data written by the future incident lifecycle service.
            jdbc.update("UPDATE alerts SET status=?, acknowledged_at=?, escalated_at=? WHERE id=?",
                    status.name(), status == AlertStatus.ACKNOWLEDGED ? NOW.plusMinutes(1) : null,
                    status == AlertStatus.ESCALATED ? NOW.plusMinutes(2) : null, saved.getId());
            Alert loaded = alerts.findByIdAndOrganizationId(saved.getId(), ORG).orElseThrow();
            assertEquals(status, loaded.getStatus());
            if (status == AlertStatus.ACKNOWLEDGED)
                assertEquals(NOW.plusMinutes(1).toInstant(), loaded.getAcknowledgedAt());
            if (status == AlertStatus.ESCALATED)
                assertEquals(NOW.plusMinutes(2).toInstant(), loaded.getEscalatedAt());
        }
    }

    @Test void equalRaisedTimesHaveStableIdentifierOrder() {
        for (int i = 0; i < 3; i++) alerts.save(Alert.raise(incident(ORG, ZONE), AlertSeverity.LOW, "Same time", NOW.toInstant()));
        var actual = alerts.findAlerts(new GetAlerts(ORG, null, null)).stream().map(a -> a.id().toString()).toList();
        assertEquals(actual.stream().sorted().toList(), actual);
    }
}
