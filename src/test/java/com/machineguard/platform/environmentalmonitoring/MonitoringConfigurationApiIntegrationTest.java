package com.machineguard.platform.environmentalmonitoring;

import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.ThresholdConfigured;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.infrastructure.security.JwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:monitoringconfiguration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@RecordApplicationEvents
class MonitoringConfigurationApiIntegrationTest {
    static final UUID ORG = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();
    static final String BASE = "/api/v1/environmental-monitoring";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEvents events;

    @BeforeEach
    void setup() {
        for (String table : List.of("traceability_report_excursions", "traceability_reports", "non_conformities", "excursions",
                "measurements", "thresholds", "sensor_nodes", "monitoring_points", "monitoring_zones")) jdbc.update("DELETE FROM " + table);
        events.clear();
    }
    String auth(UUID org, Role role) {
        return "Bearer " + tokens.issueAccessToken(UUID.randomUUID(), org, role, UUID.randomUUID()).value();
    }
    ResultActions send(MockHttpServletRequestBuilder request, UUID org, Role role, Map<String, Object> body) throws Exception {
        return mvc.perform(request.header("Authorization", auth(org, role)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }
    JsonNode created(String path, Map<String, Object> body) throws Exception {
        return json.readTree(send(post(BASE + path), ORG, Role.ADMIN, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }
    String zone() throws Exception { return created("/zones", Map.of("name", "Cold Room A", "description", "Storage")).get("id").asText(); }
    String point(String zoneId) throws Exception {
        return created("/zones/" + zoneId + "/points", Map.of("name", "Point A", "location", "Shelf B")).get("id").asText();
    }
    ResultActions threshold(String zoneId, String variable, Object minimum, Object maximum) throws Exception {
        return send(put(BASE + "/zones/" + zoneId + "/thresholds/" + variable), ORG, Role.ADMIN,
                Map.of("minimumValue", minimum, "maximumValue", maximum));
    }

    @Test void emptyTenantIsConfiguredThroughTheApiAndDeviationOpensAnExcursion() throws Exception {
        String zoneId = zone();
        String pointId = point(zoneId);
        String sensorId = created("/zones/" + zoneId + "/points/" + pointId + "/sensors",
                Map.of("deviceCode", "MG-01", "samplingIntervalSeconds", 60)).get("id").asText();
        threshold(zoneId, "TEMPERATURE", 2, 8).andExpect(status().isOk()).andExpect(jsonPath("$.maximumValue").value(8.00));
        send(post(BASE + "/measurements"), ORG, Role.ADMIN, Map.of("monitoringZoneId", zoneId, "monitoringPointId", pointId,
                "sensorNodeId", sensorId, "environmentalVariable", "TEMPERATURE", "measuredValue", 11.2,
                "recordedAt", OffsetDateTime.now().minusMinutes(1).toString())).andExpect(status().isCreated());
        mvc.perform(get(BASE + "/zones/" + zoneId).header("Authorization", auth(ORG, Role.VIEWER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.environmentalCondition").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.points.length()").value(1)).andExpect(jsonPath("$.sensorsOnline").value(1))
                .andExpect(jsonPath("$.thresholds.length()").value(1));
        mvc.perform(get("/api/v1/traceability/excursions/open").header("Authorization", auth(ORG, Role.VIEWER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void zoneRegistrationReturnsLocationAndEmptyProjection() throws Exception {
        send(post(BASE + "/zones"), ORG, Role.ADMIN, Map.of("name", "  Cold Room A  "))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Cold Room A")).andExpect(jsonPath("$.organizationId").value(ORG.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.environmentalCondition").value("NO_DATA"));
    }
    @Test void thresholdIsReplacedInPlaceAndPublishesThresholdConfigured() throws Exception {
        String zoneId = zone();
        threshold(zoneId, "HUMIDITY", 35, 60).andExpect(status().isOk());
        threshold(zoneId, "HUMIDITY", 40, 55.5).andExpect(status().isOk())
                .andExpect(jsonPath("$.minimumValue").value(40.00)).andExpect(jsonPath("$.maximumValue").value(55.50));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM thresholds", Integer.class));
        assertEquals(2, events.stream(ThresholdConfigured.class).count());
        assertTrue(events.stream(ThresholdConfigured.class).allMatch(e -> e.organizationId().equals(ORG)));
    }
    @Test void invalidRangeAndDuplicateDeviceCodeAreConflicts() throws Exception {
        String zoneId = zone();
        String pointId = point(zoneId);
        threshold(zoneId, "TEMPERATURE", 8, 2).andExpect(status().isConflict());
        threshold(zoneId, "TEMPERATURE", 5, 5).andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM thresholds", Integer.class));
        Map<String, Object> sensor = Map.of("deviceCode", "MG-01", "samplingIntervalSeconds", 60);
        created("/zones/" + zoneId + "/points/" + pointId + "/sensors", sensor);
        send(post(BASE + "/zones/" + zoneId + "/points/" + pointId + "/sensors"), ORG, Role.ADMIN, sensor).andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM sensor_nodes", Integer.class));
    }
    @Test void invalidBodiesAndVariablesAreRejected() throws Exception {
        String zoneId = zone();
        String pointId = point(zoneId);
        send(post(BASE + "/zones"), ORG, Role.ADMIN, Map.of("name", " ")).andExpect(status().isBadRequest());
        send(post(BASE + "/zones/" + zoneId + "/points"), ORG, Role.ADMIN, Map.of("location", "Shelf")).andExpect(status().isBadRequest());
        send(post(BASE + "/zones/" + zoneId + "/points/" + pointId + "/sensors"), ORG, Role.ADMIN,
                Map.of("deviceCode", "MG-01", "samplingIntervalSeconds", 0)).andExpect(status().isBadRequest());
        threshold(zoneId, "PRESSURE", 2, 8).andExpect(status().isBadRequest());
        threshold(zoneId, "TEMPERATURE", 2.123, 8).andExpect(status().isBadRequest());
    }
    @Test void anotherTenantCannotConfigureTheZone() throws Exception {
        String zoneId = zone();
        String pointId = point(zoneId);
        send(post(BASE + "/zones/" + zoneId + "/points"), OTHER, Role.ADMIN, Map.of("name", "Intruder")).andExpect(status().isNotFound());
        send(post(BASE + "/zones/" + zoneId + "/points/" + pointId + "/sensors"), OTHER, Role.ADMIN,
                Map.of("deviceCode", "MG-99", "samplingIntervalSeconds", 60)).andExpect(status().isNotFound());
        send(put(BASE + "/zones/" + zoneId + "/thresholds/TEMPERATURE"), OTHER, Role.ADMIN,
                Map.of("minimumValue", 2, "maximumValue", 8)).andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM monitoring_points", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM thresholds", Integer.class));
    }
    @Test void pointFromAnotherZoneCannotReceiveSensors() throws Exception {
        String pointId = point(zone());
        String otherZoneId = zone();
        send(post(BASE + "/zones/" + otherZoneId + "/points/" + pointId + "/sensors"), ORG, Role.ADMIN,
                Map.of("deviceCode", "MG-01", "samplingIntervalSeconds", 60)).andExpect(status().isNotFound());
    }
    @Test void configurationRequiresAuthenticationAndAdminRole() throws Exception {
        String zoneId = zone();
        mvc.perform(post(BASE + "/zones").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Zone\"}"))
                .andExpect(status().isUnauthorized());
        send(post(BASE + "/zones"), ORG, Role.VIEWER, Map.of("name", "Zone")).andExpect(status().isForbidden());
        send(post(BASE + "/zones/" + zoneId + "/points"), ORG, Role.VIEWER, Map.of("name", "Point")).andExpect(status().isForbidden());
        send(put(BASE + "/zones/" + zoneId + "/thresholds/TEMPERATURE"), ORG, Role.VIEWER,
                Map.of("minimumValue", 2, "maximumValue", 8)).andExpect(status().isForbidden());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM monitoring_zones", Integer.class));
    }
    @Test void swaggerContainsConfigurationContract() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + BASE + "/zones'].post").exists())
                .andExpect(jsonPath("$.paths['" + BASE + "/zones/{zoneId}/points'].post").exists())
                .andExpect(jsonPath("$.paths['" + BASE + "/zones/{zoneId}/points/{pointId}/sensors'].post").exists())
                .andExpect(jsonPath("$.paths['" + BASE + "/zones/{zoneId}/thresholds/{environmentalVariable}'].put").exists());
    }
}
