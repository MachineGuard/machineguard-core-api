package com.machineguard.platform.environmentalmonitoring;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MeasurementSourceMigrationIntegrationTest {
    @Test void populatedV9KeepsEvidenceAndNeverInfersPhysicalSource() {
        String url="jdbc:h2:mem:sourceupgrade"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url,"sa","").target("9").load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
        UUID org=UUID.randomUUID(),zone=UUID.randomUUID(),point=UUID.randomUUID(),sensor=UUID.randomUUID();
        UUID measurement=UUID.randomUUID(),incident=UUID.randomUUID(),report=UUID.randomUUID();
        var at=OffsetDateTime.parse("2026-11-04T12:00:00Z");String content="{\"sealed\":\"original evidence\"}";
        jdbc.update("INSERT INTO monitoring_zones(id,organization_id,name,status,created_at,updated_at) VALUES (?,?,'Legacy','ACTIVE',?,?)",zone,org,at,at);
        jdbc.update("INSERT INTO monitoring_points(id,monitoring_zone_id,name,status,created_at,updated_at) VALUES (?,?,'Point','ACTIVE',?,?)",point,zone,at,at);
        jdbc.update("INSERT INTO sensor_nodes(id,monitoring_point_id,device_code,sampling_interval_seconds,status,created_at) VALUES (?,?,'Legacy sensor',10,'ONLINE',?)",sensor,point,at);
        jdbc.update("INSERT INTO measurements(id,organization_id,monitoring_zone_id,monitoring_point_id,sensor_node_id,environmental_variable,measured_value,recorded_at,received_at) VALUES (?,?,?,?,?,'TEMPERATURE',5,?,?)",measurement,org,zone,point,sensor,at,at);
        jdbc.update("INSERT INTO incidents(id,origin_measurement_id,organization_id,monitoring_zone_id,status,opened_at,environmental_variable,open_key) VALUES (?,?,?,?,'OPEN',?,'TEMPERATURE','legacy')",incident,measurement,org,zone,at);
        jdbc.update("INSERT INTO traceability_reports(id,organization_id,monitoring_zone_id,period_start,period_end,generated_by,generated_at,excursion_count,measurement_count,status,content,checksum,sealed_at) VALUES (?,?,?,?,?,?,?,0,1,'SEALED',?,'original-checksum',?)",report,org,zone,at.minusHours(1),at,UUID.randomUUID(),at,content,at);
        var before=jdbc.queryForMap("SELECT * FROM measurements WHERE id=?",measurement);
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        var after=jdbc.queryForMap("SELECT * FROM measurements WHERE id=?",measurement);
        assertEquals("LEGACY_UNKNOWN",after.remove("source"));assertEquals(before,after);
        assertEquals(measurement,jdbc.queryForObject("SELECT origin_measurement_id FROM incidents WHERE id=?",UUID.class,incident));
        assertEquals(content,jdbc.queryForObject("SELECT content FROM traceability_reports WHERE id=?",String.class,report));
        assertEquals("original-checksum",jdbc.queryForObject("SELECT checksum FROM traceability_reports WHERE id=?",String.class,report));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE measurements SET source='UNTRUSTED'"));
    }
}
