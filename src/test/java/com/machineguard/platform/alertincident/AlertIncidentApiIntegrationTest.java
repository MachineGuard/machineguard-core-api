package com.machineguard.platform.alertincident;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.DeviationDetected;
import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:incidentapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
class AlertIncidentApiIntegrationTest {

    static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    static final UUID ORG = uuid(1);
    static final UUID OTHER_ORG = uuid(2);
    static final UUID USER = uuid(5);
    static final UUID ZONE = uuid(10);
    static final UUID OTHER_ZONE = uuid(11);
    static final UUID POINT = uuid(100);
    static final String INCIDENTS = "/api/v1/incidents";

    static UUID uuid(long n) {
        return new UUID(0, n);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEventPublisher events;
    @Autowired JwtTokenService jwtTokens;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("DELETE FROM traceability_report_excursions");
        jdbc.execute("DELETE FROM traceability_reports");
        jdbc.execute("DELETE FROM non_conformities");
        jdbc.execute("DELETE FROM excursions");
        jdbc.execute("DELETE FROM corrective_actions");
        jdbc.execute("DELETE FROM alerts");
        jdbc.execute("DELETE FROM incidents");
        for (String table : java.util.List.of("measurements", "thresholds", "sensor_nodes", "monitoring_points", "monitoring_zones"))
            jdbc.update("DELETE FROM " + table);
        for (UUID zone : java.util.List.of(ZONE, OTHER_ZONE)) {
            UUID point = zone.equals(ZONE) ? POINT : uuid(101);
            jdbc.update("INSERT INTO monitoring_zones(id,organization_id,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)",
                    zone, ORG, "Zone", NOW, NOW);
            jdbc.update("INSERT INTO monitoring_points(id,monitoring_zone_id,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)",
                    point, zone, "Point", NOW, NOW);
            jdbc.update("INSERT INTO sensor_nodes(id,monitoring_point_id,device_code,sampling_interval_seconds,status,created_at) VALUES (?,?,?,60,'OFFLINE',?)",
                    point,point,"fixture-"+point,NOW);
            for (String variable : java.util.List.of("TEMPERATURE", "HUMIDITY")) {
                jdbc.update("INSERT INTO thresholds(id,monitoring_zone_id,environmental_variable,minimum_value,maximum_value,created_at,updated_at) VALUES (?,?,?,2,8,?,?)",
                        UUID.randomUUID(), zone, variable, NOW, NOW);
                reading(UUID.randomUUID(), zone, point, variable, "5.00", NOW.minusSeconds(7200));
            }
        }
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID org, Role role) {
        return request.header("Authorization",
                "Bearer " + com.machineguard.platform.support.SessionFixtures.token(jdbc,jwtTokens,USER, org, role, uuid(888)));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return as(request, ORG, Role.ADMIN);
    }

    private void deviation(UUID zone, EnvironmentalVariable variable, String value, Instant at) {
        UUID measurementId = UUID.randomUUID();
        UUID point = zone.equals(ZONE) ? POINT : uuid(101);
        reading(measurementId, zone, point, variable.name(), value, at);
        events.publishEvent(new DeviationDetected(measurementId, ORG, zone, point, null, variable,
                new BigDecimal(value), new BigDecimal("2.00"), new BigDecimal("8.00"),
                OffsetDateTime.ofInstant(at, ZoneOffset.UTC)));
    }

    private void reading(UUID id, UUID zone, UUID point, String variable, String value, Instant at) {
        jdbc.update("INSERT INTO measurements(id,organization_id,monitoring_zone_id,monitoring_point_id,sensor_node_id,source,environmental_variable,measured_value,recorded_at,received_at) VALUES (?,?,?,?,?,'SENSOR',?,?,?,?)",
                id, ORG, zone, point, point, variable, new BigDecimal(value), at, NOW);
    }

    private void restore() {
        UUID id = UUID.randomUUID();
        Instant at = NOW.minusSeconds(120);
        reading(id, ZONE, POINT, "TEMPERATURE", "5.00", at);
        var timestamp = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
        events.publishEvent(new com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.SafeRangeRestored(
                id, ORG, ZONE, POINT, null, EnvironmentalVariable.TEMPERATURE, new BigDecimal("5.00"), timestamp, timestamp));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode incidents(String query) throws Exception {
        return body(mvc.perform(admin(get(INCIDENTS + query))).andExpect(status().isOk()).andReturn());
    }

    /** Abre un incidente con una desviacion y devuelve su id. */
    private String openIncident() throws Exception {
        deviation(ZONE, EnvironmentalVariable.TEMPERATURE, "11.50", NOW.minusSeconds(3600));
        JsonNode list = incidents("?monitoringZoneId=" + ZONE);
        assertEquals(1, list.size());
        return list.get(0).get("id").asText();
    }

    private String alertIdOf(String incidentId) throws Exception {
        return body(mvc.perform(admin(get(INCIDENTS + "/" + incidentId))).andReturn()).get("alert").get("id").asText();
    }

    private void registerAction(String incidentId, String description) throws Exception {
        mvc.perform(admin(post(INCIDENTS + "/" + incidentId + "/corrective-actions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + description + "\",\"notes\":\"Revisar manana\"}"))
                .andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- creacion

    @Test
    void aDeviationOpensAnIncidentWithAPendingAlertAndLaterOnesJoinIt() throws Exception {
        deviation(ZONE, EnvironmentalVariable.TEMPERATURE, "9.00", NOW.minusSeconds(3000));
        deviation(ZONE, EnvironmentalVariable.TEMPERATURE, "12.50", NOW.minusSeconds(2000));
        deviation(ZONE, EnvironmentalVariable.TEMPERATURE, "10.00", NOW.minusSeconds(1000));

        JsonNode list = incidents("");
        assertEquals(1, list.size());
        assertEquals("OPEN", list.get(0).get("status").asText());
        assertEquals("TEMPERATURE", list.get(0).get("environmentalVariable").asText());

        JsonNode detail = body(mvc.perform(admin(get(INCIDENTS + "/" + list.get(0).get("id").asText())))
                .andExpect(status().isOk()).andReturn());
        assertEquals("PENDING", detail.get("alert").get("status").asText());
        assertEquals("LOW", detail.get("alert").get("severity").asText());
        assertEquals(ZONE.toString(), detail.get("alert").get("monitoringZoneId").asText());
        assertTrue(detail.get("alert").get("message").asText().contains("rango seguro"));
    }

    @Test
    void anotherVariableOrZoneOpensItsOwnIncident() throws Exception {
        deviation(ZONE, EnvironmentalVariable.TEMPERATURE, "9.00", NOW.minusSeconds(3000));
        deviation(ZONE, EnvironmentalVariable.HUMIDITY, "9.00", NOW.minusSeconds(2900));
        deviation(OTHER_ZONE, EnvironmentalVariable.TEMPERATURE, "20.00", NOW.minusSeconds(2800));

        assertEquals(3, incidents("").size());
        assertEquals(2, incidents("?monitoringZoneId=" + ZONE).size());
    }

    @Test
    void severityDependsOnHowFarTheMeasurementIsFromTheSafeRange() throws Exception {
        deviation(ZONE, EnvironmentalVariable.TEMPERATURE, "30.00", NOW.minusSeconds(3000));
        String incidentId = incidents("").get(0).get("id").asText();
        mvc.perform(admin(get(INCIDENTS + "/" + incidentId)))
                .andExpect(jsonPath("$.alert.severity").value("CRITICAL"));
    }

    // ----------------------------------------------------------------- consulta

    @Test
    void listFiltersByZoneStatusAndPeriodAndIsolatesOrganizations() throws Exception {
        deviation(ZONE, EnvironmentalVariable.TEMPERATURE, "9.00", NOW.minusSeconds(7200));
        deviation(OTHER_ZONE, EnvironmentalVariable.TEMPERATURE, "9.00", NOW.minusSeconds(600));

        assertEquals(1, incidents("?monitoringZoneId=" + OTHER_ZONE).size());
        assertEquals(2, incidents("?status=OPEN").size());
        assertEquals(0, incidents("?status=RESOLVED").size());
        assertEquals(1, incidents("?from=" + NOW.minusSeconds(1800)).size());
        assertEquals(1, incidents("?to=" + NOW.minusSeconds(3600)).size());
        assertEquals(1, incidents("?limit=1").size());

        mvc.perform(as(get(INCIDENTS), OTHER_ORG, Role.ADMIN)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(admin(get(INCIDENTS + "?from=" + NOW + "&to=" + NOW.minusSeconds(60))))
                .andExpect(status().isBadRequest());
        mvc.perform(admin(get(INCIDENTS + "?status=INVENTED"))).andExpect(status().isBadRequest());
    }

    @Test
    void viewersCanReadIncidentsAndUnauthenticatedCallsAreRejected() throws Exception {
        String incidentId = openIncident();
        mvc.perform(as(get(INCIDENTS), ORG, Role.VIEWER)).andExpect(status().isOk());
        mvc.perform(as(get(INCIDENTS + "/" + incidentId), ORG, Role.VIEWER)).andExpect(status().isOk());
        mvc.perform(get(INCIDENTS)).andExpect(status().isUnauthorized());
    }

    @Test
    void detailOfAnUnknownOrForeignIncidentIsNotFound() throws Exception {
        String incidentId = openIncident();
        mvc.perform(admin(get(INCIDENTS + "/" + uuid(999)))).andExpect(status().isNotFound());
        mvc.perform(as(get(INCIDENTS + "/" + incidentId), OTHER_ORG, Role.ADMIN)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ reconocimiento

    @Test
    void acknowledgingAnAlertRecordsWhoAndWhenAndMovesTheIncident() throws Exception {
        String incidentId = openIncident();
        String alertId = alertIdOf(incidentId);

        mvc.perform(admin(post("/api/v1/alerts/" + alertId + "/acknowledgements"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Voy en camino\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acknowledgedBy").value(USER.toString()))
                .andExpect(jsonPath("$.alertStatus").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.incidentStatus").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").value("2026-10-20T12:00:00Z"));

        mvc.perform(admin(get(INCIDENTS + "/" + incidentId)))
                .andExpect(jsonPath("$.incident.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.alert.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgement.acknowledgedBy").value(USER.toString()))
                .andExpect(jsonPath("$.acknowledgement.note").value("Voy en camino"));
    }

    @Test
    void acknowledgementWorksWithoutABodyButOnlyOnce() throws Exception {
        String alertId = alertIdOf(openIncident());
        mvc.perform(admin(post("/api/v1/alerts/" + alertId + "/acknowledgements"))).andExpect(status().isCreated());
        mvc.perform(admin(post("/api/v1/alerts/" + alertId + "/acknowledgements"))).andExpect(status().isConflict());
    }

    @Test
    void acknowledgementIsRestrictedToAdminsOfTheSameOrganization() throws Exception {
        String alertId = alertIdOf(openIncident());
        mvc.perform(as(post("/api/v1/alerts/" + alertId + "/acknowledgements"), ORG, Role.VIEWER))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/v1/alerts/" + alertId + "/acknowledgements"), OTHER_ORG, Role.ADMIN))
                .andExpect(status().isNotFound());
        mvc.perform(admin(post("/api/v1/alerts/" + uuid(999) + "/acknowledgements"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/alerts/" + alertId + "/acknowledgements")).andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------- acciones correctivas

    @Test
    void correctiveActionsAreLinkedToTheIncidentAndTheAuthenticatedUser() throws Exception {
        String incidentId = openIncident();
        registerAction(incidentId, "Se reemplazo el compresor");
        registerAction(incidentId, "Se recalibro el sensor");

        mvc.perform(admin(get(INCIDENTS + "/" + incidentId)))
                .andExpect(jsonPath("$.correctiveActions.length()").value(2))
                .andExpect(jsonPath("$.correctiveActions[0].incidentId").value(incidentId))
                .andExpect(jsonPath("$.correctiveActions[0].performedBy").value(USER.toString()))
                .andExpect(jsonPath("$.correctiveActions[0].description").value("Se reemplazo el compresor"))
                .andExpect(jsonPath("$.correctiveActions[0].notes").value("Revisar manana"));
    }

    @Test
    void correctiveActionValidationAndAccessRules() throws Exception {
        String incidentId = openIncident();
        String url = INCIDENTS + "/" + incidentId + "/corrective-actions";

        mvc.perform(admin(post(url)).contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(admin(post(url)).contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(post(url), ORG, Role.VIEWER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(as(post(url), OTHER_ORG, Role.ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(admin(post(INCIDENTS + "/" + uuid(999) + "/corrective-actions"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- resolucion

    @Test
    void resolvingWithoutACorrectiveActionIsRejected() throws Exception {
        String incidentId = openIncident();
        mvc.perform(admin(post(INCIDENTS + "/" + incidentId + "/resolve"))).andExpect(status().isConflict());
        mvc.perform(admin(get(INCIDENTS + "/" + incidentId))).andExpect(jsonPath("$.incident.status").value("OPEN"));
    }

    @Test
    void resolvingClosesTheIncidentAndTheAlertAndRejectsFurtherChanges() throws Exception {
        String incidentId = openIncident();
        String alertId = alertIdOf(incidentId);
        registerAction(incidentId, "Se reemplazo el compresor");
        restore();

        mvc.perform(admin(post(INCIDENTS + "/" + incidentId + "/resolve"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"Camara estable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedBy").value(USER.toString()))
                .andExpect(jsonPath("$.resolutionNotes").value("Camara estable"))
                .andExpect(jsonPath("$.resolvedAt").value("2026-10-20T12:00:00Z"));

        mvc.perform(admin(get(INCIDENTS + "/" + incidentId))).andExpect(jsonPath("$.alert.status").value("CLOSED"));
        mvc.perform(admin(post(INCIDENTS + "/" + incidentId + "/resolve"))).andExpect(status().isConflict());
        mvc.perform(admin(post(INCIDENTS + "/" + incidentId + "/corrective-actions"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"tarde\"}"))
                .andExpect(status().isConflict());
        mvc.perform(admin(post("/api/v1/alerts/" + alertId + "/acknowledgements"))).andExpect(status().isConflict());
    }

    @Test
    void resolvingRequiresAdminRoleAndTheSameOrganization() throws Exception {
        String incidentId = openIncident();
        registerAction(incidentId, "Se reemplazo el compresor");
        mvc.perform(as(post(INCIDENTS + "/" + incidentId + "/resolve"), ORG, Role.VIEWER))
                .andExpect(status().isForbidden());
        mvc.perform(as(post(INCIDENTS + "/" + incidentId + "/resolve"), OTHER_ORG, Role.ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void aNewDeviationOpensANewIncidentOnceTheZoneIsResolved() throws Exception {
        String incidentId = openIncident();
        registerAction(incidentId, "Se reemplazo el compresor");
        restore();
        mvc.perform(admin(post(INCIDENTS + "/" + incidentId + "/resolve"))).andExpect(status().isOk());

        deviation(ZONE, EnvironmentalVariable.TEMPERATURE, "10.00", NOW.minusSeconds(60));
        assertEquals(2, incidents("?monitoringZoneId=" + ZONE).size());
        assertEquals(1, incidents("?status=OPEN").size());
        assertEquals(1, incidents("?status=RESOLVED").size());
    }

    // -------------------------------------------------------------- trazabilidad

    @Test
    void resolvingLinksTheCorrectiveActionToTheTraceabilityExcursionAndClosesIt() throws Exception {
        String incidentId = openIncident();
        registerAction(incidentId, "Se reemplazo el compresor");
        registerAction(incidentId, "Se recalibro el sensor");

        JsonNode before = body(mvc.perform(admin(get("/api/v1/traceability/excursions?status=ONGOING")))
                .andExpect(status().isOk()).andReturn());
        assertEquals(1, before.size());
        assertTrue(before.get(0).get("incidentId").isNull());

        restore();
        mvc.perform(admin(post(INCIDENTS + "/" + incidentId + "/resolve"))).andExpect(status().isOk());

        JsonNode closed = body(mvc.perform(admin(get("/api/v1/traceability/excursions?status=CLOSED")))
                .andExpect(status().isOk()).andReturn());
        assertEquals(1, closed.size());
        assertEquals(incidentId, closed.get(0).get("incidentId").asText());
        assertEquals("Se reemplazo el compresor | Se recalibro el sensor",
                closed.get(0).get("correctiveAction").asText());
        assertEquals("CLOSED", closed.get(0).get("status").asText());
    }
}
