package com.machineguard.platform.alertincident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machineguard.platform.alertincident.application.commandservices.RaiseAlertCommandService;
import com.machineguard.platform.alertincident.domain.commands.AlertIncidentCommands.RaiseAlertCommand;
import com.machineguard.platform.alertincident.domain.events.AlertIncidentEvents.AlertRaised;
import com.machineguard.platform.alertincident.domain.model.EnvironmentalVariable;
import com.machineguard.platform.environmentalmonitoring.application.commandservices.RegisterMeasurementCommandService;
import com.machineguard.platform.environmentalmonitoring.domain.commands.RegisterMeasurementCommand;
import com.machineguard.platform.iam.domain.model.*;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.infrastructure.persistence.OrganizationEntity;
import com.machineguard.platform.iam.infrastructure.persistence.UserEntity;
import com.machineguard.platform.iam.infrastructure.persistence.repository.OrganizationJpaRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.UserJpaRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:alertincidentflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}")
@AutoConfigureMockMvc
class AlertIncidentFlowIntegrationTest {
    static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    static final UUID ORG = new UUID(0, 801), OTHER = new UUID(0, 802), ADMIN = new UUID(0, 803);
    static final String PASSWORD = "integration test password";
    static final String MONITORING = "/api/v1/environmental-monitoring";

    @TestConfiguration
    static class Configuration {
        @Bean @Primary Clock flowClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean FailingAlertConsumer failingAlertConsumer() { return new FailingAlertConsumer(); }
    }
    static class FailingAlertConsumer {
        volatile boolean enabled;
        @EventListener public void on(AlertRaised event) {
            if (enabled) throw new IllegalStateException("Simulated alert persistence consumer failure");
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizationJpaRepository organizations;
    @Autowired UserJpaRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired RegisterMeasurementCommandService measurementCommands;
    @Autowired RaiseAlertCommandService generation;
    @Autowired FailingAlertConsumer failing;
    String admin, viewer, other;
    UUID zone, point;

    @BeforeEach void setup() throws Exception {
        failing.enabled = false;
        for (String table : List.of("traceability_report_excursions", "traceability_reports", "non_conformities",
                "excursions", "corrective_actions", "alerts", "incidents", "measurements", "thresholds", "sensor_nodes",
                "monitoring_points", "monitoring_zones", "initial_admin_invitations", "authentication_refresh_token_history",
                "authentication_sessions", "users", "organizations")) jdbc.update("DELETE FROM " + table);
        organizations.save(new OrganizationEntity(ORG, "North", SubscriptionPlan.FREE, OrganizationStatus.ACTIVE, null, NOW));
        organizations.save(new OrganizationEntity(OTHER, "South", SubscriptionPlan.FREE, OrganizationStatus.ACTIVE, null, NOW));
        String hash = passwords.encode(PASSWORD);
        users.save(new UserEntity(ADMIN, ORG, "admin@flow.test", hash, "Admin", Role.ADMIN, UserStatus.ACTIVE, NOW));
        users.save(new UserEntity(new UUID(0, 804), ORG, "viewer@flow.test", hash, "Viewer", Role.VIEWER, UserStatus.ACTIVE, NOW));
        users.save(new UserEntity(new UUID(0, 805), OTHER, "other@flow.test", hash, "Other", Role.ADMIN, UserStatus.ACTIVE, NOW));
        admin = login("admin@flow.test"); viewer = login("viewer@flow.test"); other = login("other@flow.test");
        zone = UUID.fromString(send(post(MONITORING + "/zones"), admin, Map.of("name", "Cold Room"), 201).get("id").asText());
        point = addPoint("Point A");
        send(put(MONITORING + "/zones/" + zone + "/thresholds/TEMPERATURE"), admin,
                Map.of("minimumValue", 2, "maximumValue", 8), 200);
    }
    @AfterEach void resetFailure() { failing.enabled = false; }
    String login(String email) throws Exception {
        return "Bearer " + send(post("/api/v1/auth/login"), null,
                Map.of("email", email, "password", PASSWORD), 200).get("accessToken").asText();
    }
    JsonNode send(MockHttpServletRequestBuilder request, String auth, Object body, int expected) throws Exception {
        if (auth != null) request.header("Authorization", auth);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
        String text = result.getResponse().getContentAsString();
        return text.isEmpty() ? json.nullNode() : json.readTree(text);
    }
    UUID addPoint(String name) throws Exception {
        return UUID.fromString(send(post(MONITORING + "/zones/" + zone + "/points"), admin,
                Map.of("name", name), 201).get("id").asText());
    }
    JsonNode measurement(UUID target, int seconds, int value) throws Exception {
        return send(post(MONITORING + "/measurements"), admin, Map.of("monitoringZoneId", zone,
                "monitoringPointId", target, "environmentalVariable", "TEMPERATURE", "measuredValue", value,
                "recordedAt", NOW.minusSeconds(seconds).toString()), 201);
    }
    JsonNode incidents() throws Exception { return send(get("/api/v1/incidents"), admin, null, 200); }
    String incidentId() throws Exception { return incidents().get(0).get("id").asText(); }
    String alertId() throws Exception { return send(get("/api/v1/alerts"), admin, null, 200).get(0).get("id").asText(); }
    String resolveUrl(String incidentId) { return "/api/v1/incidents/" + incidentId + "/resolve"; }
    void action(String incidentId) throws Exception {
        send(post("/api/v1/incidents/" + incidentId + "/corrective-actions"), admin,
                Map.of("description", "Repaired cooling", "performedBy", new UUID(0, 999), "organizationId", OTHER), 201);
    }
    long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }







    @Test void failedAlertGenerationRollsBackMeasurementIncidentAlertReceiptAndExcursion() throws Exception {
        failing.enabled = true;
        assertThrows(IllegalStateException.class, () -> measurementCommands.handle(new RegisterMeasurementCommand(ORG, zone,
                point, null, com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable.TEMPERATURE,
                new BigDecimal("11"), OffsetDateTime.ofInstant(NOW.minusSeconds(600), ZoneOffset.UTC))));
        for (String table : List.of("measurements", "incidents", "alerts", "incident_measurements", "excursions"))
            assertEquals(0, count(table), table);
        failing.enabled = false;
        measurement(point, 600, 11);
        assertEquals(1, count("incidents")); assertEquals(1, count("alerts"));
    }



    int concurrentPost(String url, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
        return mvc.perform(post(url).header("Authorization", admin)).andReturn().getResponse().getStatus();
    }
    List<Integer> race(String url) throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> concurrentPost(url, ready, start));
            var second = executor.submit(() -> concurrentPost(url, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            return java.util.stream.Stream.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)).sorted().toList();
        }
    }



    @Test void simultaneousDeviationsFromTwoPointsCreateOnlyOneIncidentAndAlert() throws Exception {
        UUID second = addPoint("Point B");
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return measurement(point, 600, 11); });
            var next = executor.submit(() -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return measurement(second, 590, 12); });
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            first.get(20, TimeUnit.SECONDS); next.get(20, TimeUnit.SECONDS);
        }
        assertEquals(2, count("measurements")); assertEquals(2, count("incident_measurements"));
        assertEquals(1, count("incidents")); assertEquals(1, count("alerts")); assertEquals(1, count("excursions"));
    }
}
