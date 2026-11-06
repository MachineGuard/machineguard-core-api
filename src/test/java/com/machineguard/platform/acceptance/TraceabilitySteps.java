package com.machineguard.platform.acceptance;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.machineguard.platform.traceability.application.integration.InboundEvents.DeviationDetected;
import com.machineguard.platform.traceability.application.integration.InboundEvents.IncidentResolved;
import com.machineguard.platform.traceability.application.integration.InboundEvents.SafeRangeRestored;
import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.machineguard.platform.acceptance.IamSteps.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Pasos de Traceability & Quality (US02, US10, US11, US12).
 * Los eventos de Environmental Monitoring y Alert & Incident Management se publican como eventos Spring,
 * igual que lo harán esos bounded contexts al integrarse en la API central.
 */
public class TraceabilitySteps {

    private static final String BASE = "/api/v1/traceability";
    private static final double TEMPERATURE_LIMIT = 8.0;

    private final ScenarioContext context;
    private final MockMvc mvc;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public TraceabilitySteps(ScenarioContext context, MockMvc mvc, JsonMapper json, JdbcTemplate jdbc,
                             ApplicationEventPublisher events) {
        this.context = context;
        this.mvc = mvc;
        this.json = json;
        this.jdbc = jdbc;
        this.events = events;
    }

    // ---------- Datos iniciales ----------

    @Dado("que la organización {string} tiene la zona de monitoreo {string}")
    public void organizationHasZone(String organization, String zone) {
        UUID organizationId = context.organization(organization);
        UUID zoneId = UUID.randomUUID();
        UUID pointId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO monitoring_zones (id, organization_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)", zoneId, organizationId, zone, now, now);
        jdbc.update("INSERT INTO monitoring_points (id, monitoring_zone_id, name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)", pointId, zoneId, zone + " - Punto 1", now, now);
        context.zones.put(zone, zoneId);
        context.points.put(zone, pointId);
        context.zoneOrganizations.put(zone, organizationId);
    }

    @Dado("que en {string} se registró una medición de temperatura de {double} grados hace {int} minutos")
    public void measurementRecorded(String zone, double value, int minutesAgo) {
        Timestamp at = Timestamp.from(minutesAgo(minutesAgo));
        jdbc.update("INSERT INTO measurements (id, organization_id, monitoring_zone_id, monitoring_point_id, "
                        + "environmental_variable, measured_value, recorded_at, received_at) "
                        + "VALUES (?, ?, ?, ?, 'TEMPERATURE', ?, ?, ?)",
                UUID.randomUUID(), context.zoneOrganizations.get(zone), context.zone(zone), context.points.get(zone),
                value, at, at);
    }

    // ---------- Eventos de otros bounded contexts ----------

    @Dado("que en {string} se detectó una temperatura de {double} grados fuera del rango seguro hace {int} minutos")
    public void deviationDetected(String zone, double value, int minutesAgo) {
        events.publishEvent(new DeviationDetected(context.zoneOrganizations.get(zone), context.zone(zone),
                context.points.get(zone), EnvironmentalVariable.TEMPERATURE, value, TEMPERATURE_LIMIT,
                minutesAgo(minutesAgo)));
    }

    @Dado("que la temperatura de {string} volvió al rango seguro hace {int} minutos")
    public void safeRangeRestored(String zone, int minutesAgo) {
        events.publishEvent(new SafeRangeRestored(context.zoneOrganizations.get(zone), context.zone(zone),
                EnvironmentalVariable.TEMPERATURE, minutesAgo(minutesAgo)));
    }

    @Dado("que el incidente de {string} se resolvió hace {int} minutos con la acción correctiva {string}")
    public void incidentResolved(String zone, int minutesAgo, String correctiveAction) {
        events.publishEvent(new IncidentResolved(context.zoneOrganizations.get(zone), UUID.randomUUID(),
                context.zone(zone), EnvironmentalVariable.TEMPERATURE, correctiveAction, minutesAgo(minutesAgo)));
    }

    // ---------- US02: historial de mediciones ----------

    @Cuando("{string} consulta el historial de mediciones de {string} de los últimos {int} minutos")
    public void requestsMeasurementHistory(String email, String zone, int minutes) throws Exception {
        Instant to = Instant.now();
        requestMeasurementHistory(email, zone, to.minus(Duration.ofMinutes(minutes)), to);
    }

    @Cuando("{string} consulta el historial de mediciones de {string} con un período invertido")
    public void requestsMeasurementHistoryWithInvertedPeriod(String email, String zone) throws Exception {
        Instant now = Instant.now();
        requestMeasurementHistory(email, zone, now, now.minus(Duration.ofHours(1)));
    }

    @Entonces("el historial contiene {int} mediciones")
    public void historyContains(int count) {
        assertEquals(200, context.lastStatus(), () -> context.lastBodyText());
        JsonNode body = context.lastBody();
        assertEquals(count, body.get("count").asInt());
        assertEquals(count, body.get("measurements").size());
    }

    // ---------- US10: excursiones ----------

    @Cuando("{string} consulta las excursiones de {string}")
    public void requestsZoneExcursions(String email, String zone) throws Exception {
        context.lastResult = mvc.perform(bearer(get(BASE + "/excursions"), context.accessToken(email))
                .param("monitoringZoneId", context.zone(zone).toString())).andReturn();
    }

    @Cuando("{string} consulta las excursiones abiertas")
    public void requestsOpenExcursions(String email) throws Exception {
        context.lastResult = mvc.perform(bearer(get(BASE + "/excursions/open"), context.accessToken(email))).andReturn();
    }

    @Cuando("{string} consulta las excursiones de {string} desde hace {int} horas")
    public void requestsExcursionsSince(String email, String zone, int hours) throws Exception {
        context.lastResult = mvc.perform(bearer(get(BASE + "/excursions"), context.accessToken(email))
                .param("monitoringZoneId", context.zone(zone).toString())
                .param("from", Instant.now().minus(Duration.ofHours(hours)).toString())).andReturn();
    }

    @Entonces("la primera excursión tiene el estado {string}")
    public void firstExcursionHasStatus(String status) {
        assertEquals(status, firstListItem().get("status").asText());
    }

    @Entonces("la primera excursión tiene un valor pico de {double} grados")
    public void firstExcursionHasPeak(double peak) {
        assertEquals(peak, firstListItem().get("peakValue").asDouble(), 0.001);
    }

    @Entonces("la primera excursión duró {int} minutos")
    public void firstExcursionLasted(int minutes) {
        long seconds = firstListItem().get("durationSeconds").asLong();
        assertEquals(minutes, Math.round(seconds / 60.0), () -> "Duración en segundos: " + seconds);
    }

    // ---------- US11: trazabilidad de incidentes y no conformidades ----------

    @Cuando("{string} consulta el detalle de la última excursión de {string}")
    public void requestsLatestExcursionDetail(String email, String zone) throws Exception {
        UUID excursionId = latestExcursionId(email, zone);
        context.lastResult = mvc.perform(bearer(get(BASE + "/excursions/" + excursionId), context.accessToken(email)))
                .andReturn();
    }

    @Entonces("el detalle muestra la acción correctiva {string}")
    public void detailShowsCorrectiveAction(String action) {
        JsonNode excursion = context.lastBody().get("excursion");
        assertEquals(action, excursion.get("correctiveAction").asText());
        assertFalse(excursion.get("incidentId").isNull(), "La excursión debe quedar vinculada al incidente");
    }

    @Entonces("el detalle muestra {int} no conformidades")
    public void detailShowsNonConformities(int count) {
        assertEquals(count, context.lastBody().get("nonConformities").size());
    }

    @Cuando("{string} registra el lote {string} de {string} como no conformidad de la última excursión de {string}")
    public void registersNonConformity(String email, String batch, String product, String zone) throws Exception {
        UUID excursionId = latestExcursionId(email, zone);
        String body = json.writeValueAsString(Map.of("batchCode", batch, "productName", product,
                "quantity", 100, "classification", "Cadena de frío"));
        context.lastResult = mvc.perform(bearer(post(BASE + "/excursions/" + excursionId + "/non-conformities"),
                        context.accessToken(email))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        if (context.lastStatus() == 201) {
            context.nonConformityId = UUID.fromString(context.lastBody().get("id").asText());
        }
    }

    @Entonces("el lote queda con la disposición {string}")
    public void batchHasDisposition(String disposition) {
        assertEquals(disposition, context.lastBody().get("disposition").asText());
    }

    @Cuando("{string} libera el lote sin justificación")
    public void releasesBatchWithoutJustification(String email) throws Exception {
        changeDisposition(email, "{\"disposition\":\"RELEASED\"}");
    }

    @Cuando("{string} libera el lote con la justificación {string}")
    public void releasesBatchWithJustification(String email, String justification) throws Exception {
        changeDisposition(email, json.writeValueAsString(
                Map.of("disposition", "RELEASED", "justification", justification)));
    }

    // ---------- US12: reporte de trazabilidad ----------

    @Cuando("{string} genera el reporte de trazabilidad de {string} de las últimas {int} horas")
    public void generatesReport(String email, String zone, int hours) throws Exception {
        Instant end = Instant.now();
        String body = json.writeValueAsString(Map.of("monitoringZoneId", context.zone(zone).toString(),
                "periodStart", end.minus(Duration.ofHours(hours)).toString(), "periodEnd", end.toString()));
        context.lastResult = mvc.perform(bearer(post(BASE + "/reports"), context.accessToken(email))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        if (context.lastStatus() == 201) {
            context.reportId = UUID.fromString(context.lastBody().get("id").asText());
        }
    }

    @Entonces("el reporte queda en estado {string}")
    public void reportHasStatus(String status) {
        assertEquals(status, context.lastBody().get("status").asText());
    }

    @Entonces("el reporte incluye {int} excursiones cerradas y {int} mediciones")
    public void reportIncludes(int excursions, int measurements) {
        JsonNode report = context.lastBody();
        assertEquals(excursions, report.get("excursionCount").asInt());
        assertEquals(measurements, report.get("measurementCount").asInt());
        assertEquals(measurements, report.get("content").get("measurementHistory").size());
    }

    @Entonces("el reporte registra {int} acciones correctivas")
    public void reportRegistersCorrectiveActions(int count) {
        assertEquals(count, context.lastBody().get("content").get("correctiveActions").size());
    }

    @Entonces("el reporte indica que excluyó {int} excursiones en curso")
    public void reportExcludesOngoing(int count) {
        assertEquals(count, context.lastBody().get("content").get("ongoingExcursionsExcluded").asInt());
    }

    @Entonces("el reporte está sellado con un checksum SHA-256 verificable")
    public void reportIsSealed() {
        JsonNode report = context.lastBody();
        assertEquals(64, report.get("checksum").asText().length());
        assertTrue(report.get("integrityVerified").asBoolean());
    }

    @Dado("que alguien modificó el reporte directamente en la base de datos")
    public void reportTampered() {
        jdbc.update("UPDATE traceability_reports SET measurement_count = 999 WHERE id = ?", context.reportId);
    }

    @Cuando("{string} consulta el reporte generado")
    public void requestsGeneratedReport(String email) throws Exception {
        context.lastResult = mvc.perform(bearer(get(BASE + "/reports/" + context.reportId), context.accessToken(email)))
                .andReturn();
    }

    @Entonces("el sistema detecta que la integridad del reporte no es válida")
    public void reportIntegrityFails() {
        assertEquals(200, context.lastStatus());
        assertFalse(context.lastBody().get("integrityVerified").asBoolean());
    }

    // ---------- Auxiliares ----------

    private void requestMeasurementHistory(String email, String zone, Instant from, Instant to) throws Exception {
        context.lastResult = mvc.perform(bearer(get(BASE + "/measurement-history"), context.accessToken(email))
                .param("monitoringZoneId", context.zone(zone).toString())
                .param("from", from.toString()).param("to", to.toString())).andReturn();
    }

    private void changeDisposition(String email, String body) throws Exception {
        context.lastResult = mvc.perform(bearer(patch(BASE + "/non-conformities/" + context.nonConformityId + "/disposition"),
                        context.accessToken(email))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private UUID latestExcursionId(String email, String zone) throws Exception {
        String body = mvc.perform(bearer(get(BASE + "/excursions"), context.accessToken(email))
                        .param("monitoringZoneId", context.zone(zone).toString()))
                .andReturn().getResponse().getContentAsString();
        JsonNode excursions = json.readTree(body);
        assertTrue(excursions.size() > 0, "No hay excursiones registradas para " + zone);
        return UUID.fromString(excursions.get(0).get("id").asText());
    }

    private JsonNode firstListItem() {
        JsonNode list = context.lastBody();
        assertTrue(list.size() > 0, () -> "La lista está vacía: " + context.lastBodyText());
        return list.get(0);
    }

    private static Instant minutesAgo(int minutes) {
        return Instant.now().minus(Duration.ofMinutes(minutes));
    }
}
