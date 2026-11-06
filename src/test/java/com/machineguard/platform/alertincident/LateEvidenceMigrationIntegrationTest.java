package com.machineguard.platform.alertincident;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LateEvidenceMigrationIntegrationTest {
    @Test void v9PreservesV8IncidentsAndDefaultsThemToLive(){
        String url="jdbc:h2:mem:lateupgrade"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url,"sa","").target("8").load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
        UUID id=UUID.randomUUID(),measurement=UUID.randomUUID(),alert=UUID.randomUUID();
        var at=OffsetDateTime.parse("2026-10-01T12:00:00Z");
        jdbc.update("INSERT INTO incidents(id,origin_measurement_id,organization_id,monitoring_zone_id,status,opened_at,environmental_variable,open_key) VALUES (?,?,?,?,'OPEN',?,'TEMPERATURE',?)",id,measurement,UUID.randomUUID(),UUID.randomUUID(),at,"legacy-key");
        jdbc.update("INSERT INTO alerts(id,incident_id,severity,message,status,raised_at) VALUES (?,?,'HIGH','Existing','PENDING',?)",alert,id,at);
        jdbc.update("INSERT INTO incident_measurements(measurement_id,incident_id) VALUES (?,?)",measurement,id);
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        assertFalse(jdbc.queryForObject("SELECT historical FROM incidents WHERE id=?",Boolean.class,id));
        assertEquals(at.toInstant(),jdbc.queryForObject("SELECT live_started_at FROM incidents WHERE id=?",OffsetDateTime.class,id).toInstant());
        assertEquals(measurement,jdbc.queryForObject("SELECT origin_measurement_id FROM incidents WHERE id=?",UUID.class,id));
        assertEquals(alert,jdbc.queryForObject("SELECT id FROM alerts WHERE incident_id=?",UUID.class,id));
        assertEquals(id,jdbc.queryForObject("SELECT incident_id FROM incident_measurements WHERE measurement_id=?",UUID.class,measurement));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM late_deviations",Integer.class));
    }
}
