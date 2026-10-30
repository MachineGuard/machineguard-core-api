package com.machineguard.platform.alertincident;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class IncidentMigrationIntegrationTest {
    @Test void upgradingV6PreservesTheOriginAlertAndReplayAssociation() {
        String url = "jdbc:h2:mem:upgrade" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("6").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        UUID incident = UUID.randomUUID(), measurement = UUID.randomUUID(), alert = UUID.randomUUID();
        var at = OffsetDateTime.parse("2026-10-28T12:00:00Z");
        jdbc.update("INSERT INTO incidents(id,deviation_id,organization_id,monitoring_zone_id,status,opened_at,environmental_variable,open_key) VALUES (?,?,?,?,'OPEN',?,'TEMPERATURE',?)",
                incident, measurement, UUID.randomUUID(), UUID.randomUUID(), at, "legacy-open-key");
        jdbc.update("INSERT INTO alerts(id,incident_id,severity,message,status,raised_at) VALUES (?,?,'HIGH','Existing alert','PENDING',?)",
                alert, incident, at);
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        assertEquals(measurement, jdbc.queryForObject("SELECT origin_measurement_id FROM incidents WHERE id=?", UUID.class, incident));
        assertEquals(incident, jdbc.queryForObject("SELECT incident_id FROM incident_measurements WHERE measurement_id=?", UUID.class, measurement));
        assertEquals(alert, jdbc.queryForObject("SELECT id FROM alerts WHERE incident_id=?", UUID.class, incident));
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM incidents WHERE id=?", String.class, incident));
    }
}
