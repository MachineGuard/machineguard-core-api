package com.machineguard.platform.acceptance;

import tools.jackson.databind.json.JsonMapper;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static com.machineguard.platform.acceptance.IamSteps.bearer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Acceptance steps exercise the real REST producer and consumer rather than publishing synthetic incidents. */
public class AlertIncidentSteps {
    private final ScenarioContext context;
    private final MockMvc mvc;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private Instant capturedAt = Instant.now().minusSeconds(10);
    private String gatewayCredential,deviceCode;
    private String incidentId, alertId, originMeasurementId;

    public AlertIncidentSteps(ScenarioContext context, MockMvc mvc, JsonMapper json, JdbcTemplate jdbc) {
        this.context = context; this.mvc = mvc; this.json = json; this.jdbc = jdbc;
    }

    @Dado("que {string} tiene un rango de temperatura entre {double} y {double} grados")
    public void temperatureRange(String zone, double minimum, double maximum) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO thresholds(id,monitoring_zone_id,environmental_variable,minimum_value,maximum_value,created_at,updated_at) VALUES (?,?,'TEMPERATURE',?,?,?,?)",
                UUID.randomUUID(), context.zone(zone), minimum, maximum, now, now);
    }

    @Cuando("{string} registra por API en {string} una temperatura de {double} grados")
    public void recordsMeasurement(String email, String zone, double value) throws Exception {
        capturedAt = capturedAt.plusSeconds(1);
        if (gatewayCredential==null) {
            UUID sensor=UUID.randomUUID();deviceCode="bdd-"+sensor;
            jdbc.update("INSERT INTO sensor_nodes(id,monitoring_point_id,device_code,sampling_interval_seconds,status,created_at) VALUES (?,?,?,60,'OFFLINE',?)",
                    sensor,context.points.get(zone),deviceCode,Timestamp.from(Instant.now()));
            jdbc.update("INSERT INTO thresholds(id,monitoring_zone_id,environmental_variable,minimum_value,maximum_value,created_at,updated_at) VALUES (?,?,'HUMIDITY',30,75,?,?)",
                    UUID.randomUUID(),context.zone(zone),Timestamp.from(Instant.now()),Timestamp.from(Instant.now()));
            var issued=mvc.perform(bearer(post("/api/v1/gateways"),context.accessToken(email)).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("name","BDD physical gateway","sensorNodeIds",java.util.List.of(sensor))))).andReturn();
            assertEquals(201,issued.getResponse().getStatus());gatewayCredential=json.readTree(issued.getResponse().getContentAsString()).path("credential").asText();
        }
        var delivered=mvc.perform(post("/api/v1/measurements").header("X-Device-Credential",gatewayCredential)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("deviceCode",deviceCode,
                "temperature",BigDecimal.valueOf(value),"humidity",50,"capturedAt",capturedAt.toString())))).andReturn();
        assertEquals(201,delivered.getResponse().getStatus());
        UUID measurement=jdbc.queryForObject("SELECT id FROM measurements WHERE monitoring_zone_id=? AND environmental_variable='TEMPERATURE' AND recorded_at=?",UUID.class,context.zone(zone),Timestamp.from(capturedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
        context.lastResult=delivered;
        assertEquals(201, context.lastStatus(), context.lastBodyText());
        if (originMeasurementId == null) {
            originMeasurementId = measurement.toString();
            var result = mvc.perform(bearer(get("/api/v1/alerts").param("monitoringZoneId", context.zone(zone).toString()),
                    context.accessToken(email))).andReturn();
            var alert = json.readTree(result.getResponse().getContentAsString()).get(0);
            alertId = alert.get("id").asText(); incidentId = alert.get("incidentId").asText();
        }
    }

    @Cuando("{string} consulta las alertas de {string}")
    public void queriesAlerts(String email, String zone) throws Exception {
        context.lastResult = mvc.perform(bearer(get("/api/v1/alerts").param("monitoringZoneId", context.zone(zone).toString()),
                context.accessToken(email))).andReturn();
    }

    @Entonces("la alerta conserva su zona y el incidente de la primera medición")
    public void alertKeepsItsIdentity() {
        var alert = context.lastBody().get(0);
        assertEquals(alertId, alert.get("id").asText()); assertEquals(incidentId, alert.get("incidentId").asText());
        assertTrue(context.zones.containsValue(UUID.fromString(alert.get("monitoringZoneId").asText())));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM incidents", Integer.class));
    }

    @Cuando("{string} reconoce la alerta generada")
    public void acknowledges(String email) throws Exception {
        context.lastResult = mvc.perform(bearer(post("/api/v1/alerts/" + alertId + "/acknowledgements"), context.accessToken(email))).andReturn();
    }
    @Entonces("el reconocimiento pertenece a {string}")
    public void acknowledgementActor(String email) {
        assertEquals(context.users.get(email).toString(), context.lastBody().get("acknowledgedBy").asText());
    }
    @Cuando("{string} registra la acción {string} en el incidente generado")
    public void correctiveAction(String email, String description) throws Exception {
        context.lastResult = mvc.perform(bearer(post("/api/v1/incidents/" + incidentId + "/corrective-actions"), context.accessToken(email))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("description", description)))).andReturn();
        assertEquals(201, context.lastStatus(), context.lastBodyText());
    }
    @Cuando("{string} resuelve el incidente generado")
    public void resolves(String email) throws Exception {
        context.lastResult = mvc.perform(bearer(post("/api/v1/incidents/" + incidentId + "/resolve"), context.accessToken(email))).andReturn();
    }
    @Entonces("el incidente generado conserva el estado {string} para {string}")
    public void incidentStatus(String status, String email) throws Exception {
        var result = mvc.perform(bearer(get("/api/v1/incidents/" + incidentId), context.accessToken(email))).andReturn();
        assertEquals(status, json.readTree(result.getResponse().getContentAsString()).get("incident").get("status").asText());
    }
    @Entonces("la resolución conserva la primera medición y al responsable {string}")
    public void resolvedIdentity(String email) {
        assertEquals(originMeasurementId, context.lastBody().get("originMeasurementId").asText());
        assertEquals(context.users.get(email).toString(), context.lastBody().get("resolvedBy").asText());
        assertEquals("RESOLVED", context.lastBody().get("status").asText());
    }
}
