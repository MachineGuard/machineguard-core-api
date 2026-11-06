package com.machineguard.platform.alertincident;

import com.machineguard.platform.alertincident.domain.model.*;
import com.machineguard.platform.alertincident.domain.repositories.AlertRepository;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:alertapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}",
        "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
class AlertApiIntegrationTest {
    static final String BASE = "/api/v1/alerts";
    static final UUID ORG = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();
    static final UUID ZONE_A = UUID.randomUUID();
    static final UUID ZONE_B = UUID.randomUUID();
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-28T15:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired AlertRepository alerts;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    Alert pending;
    Alert acknowledged;
    Alert foreign;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM alerts");
        jdbc.update("DELETE FROM corrective_actions");
        jdbc.update("DELETE FROM incidents");
        pending = fixture(ORG, ZONE_A, AlertStatus.PENDING, 0);
        acknowledged = fixture(ORG, ZONE_B, AlertStatus.ACKNOWLEDGED, 1);
        foreign = fixture(OTHER, ZONE_A, AlertStatus.PENDING, 2);
    }

    Alert fixture(UUID org, UUID zone, AlertStatus status, int minute) {
        UUID incidentId = UUID.randomUUID();
        jdbc.update("INSERT INTO incidents (id, origin_measurement_id, organization_id, monitoring_zone_id, status, opened_at) VALUES (?, ?, ?, ?, 'OPEN', ?)",
                incidentId, UUID.randomUUID(), org, zone, NOW);
        Alert alert = alerts.save(Alert.raise(incidentId, AlertSeverity.HIGH, "Temperature outside safe range", NOW.plusMinutes(minute).toInstant()));
        jdbc.update("UPDATE alerts SET status=?, acknowledged_at=? WHERE id=?", status.name(),
                status == AlertStatus.ACKNOWLEDGED ? NOW.plusMinutes(3) : null, alert.getId());
        return alert;
    }

    String auth(UUID org, Role role) {
        return "Bearer " + com.machineguard.platform.support.SessionFixtures.token(jdbc,tokens,UUID.randomUUID(), org, role, UUID.randomUUID());
    }

    @Test void adminAndViewerCanListAndReadDetails() throws Exception {
        for (Role role : Role.values()) {
            String token = auth(ORG, role);
            mvc.perform(get(BASE).header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(acknowledged.getId().toString()))
                    .andExpect(jsonPath("$[1].id").value(pending.getId().toString()));
            mvc.perform(get(BASE + "/" + pending.getId()).header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(pending.getId().toString()))
                    .andExpect(jsonPath("$.incidentId").value(pending.getIncidentId().toString()))
                    .andExpect(jsonPath("$.severity").value("HIGH"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.message").value("Temperature outside safe range"))
                    .andExpect(jsonPath("$.raisedAt").value(NOW.toInstant().toString()))
                    .andExpect(jsonPath("$.acknowledgedAt").value(nullValue()))
                    .andExpect(jsonPath("$.escalatedAt").value(nullValue()));
        }
    }

    @Test void filtersZoneStateAndTheirIntersection() throws Exception {
        String token = auth(ORG, Role.VIEWER);
        mvc.perform(get(BASE).header("Authorization", token).param("monitoringZoneId", ZONE_A.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id").value(contains(pending.getId().toString())));
        mvc.perform(get(BASE).header("Authorization", token).param("status", "ACKNOWLEDGED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id").value(contains(acknowledged.getId().toString())));
        mvc.perform(get(BASE).header("Authorization", token).param("monitoringZoneId", ZONE_A.toString()).param("status", "PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id").value(contains(pending.getId().toString())));
        mvc.perform(get(BASE).header("Authorization", token).param("monitoringZoneId", ZONE_A.toString()).param("status", "ACKNOWLEDGED"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void returnsEmptyForTenantWithoutAlertsOrUnknownZone() throws Exception {
        mvc.perform(get(BASE).header("Authorization", auth(UUID.randomUUID(), Role.ADMIN)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get(BASE).header("Authorization", auth(ORG, Role.ADMIN)).param("monitoringZoneId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void bothRolesAreIsolatedEvenWithForgedIdentityHeadersAndFilters() throws Exception {
        for (Role role : Role.values()) {
            String token = auth(OTHER, role);
            mvc.perform(get(BASE).header("Authorization", token).header("X-Organization-Id", ORG.toString())
                            .param("organizationId", ORG.toString()).param("monitoringZoneId", ZONE_A.toString()).param("status", "PENDING"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[*].id").value(contains(foreign.getId().toString())));
            mvc.perform(get(BASE + "/" + pending.getId()).header("Authorization", token))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Alert not found"));
            mvc.perform(get(BASE + "/" + foreign.getId()).header("Authorization", auth(ORG, role)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Alert not found"));
        }
    }

    @Test void missingDetailReturns404AndInvalidParametersReturn400() throws Exception {
        String token = auth(ORG, Role.ADMIN);
        mvc.perform(get(BASE + "/" + UUID.randomUUID()).header("Authorization", token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Alert not found"));
        for (String path : new String[] {BASE + "/invalid", BASE + "?monitoringZoneId=invalid", BASE + "?status=UNKNOWN"})
            mvc.perform(get(path).header("Authorization", token))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test void anonymousAndInvalidBearerRequestsAreRejected() throws Exception {
        for (String path : new String[] {BASE, BASE + "/" + pending.getId()}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        }
    }

    @Test void authenticatedPrincipalWithoutAnAllowedRoleIsForbidden() throws Exception {
        // IAM has only ADMIN/VIEWER. A test-only authenticated principal exercises the method guard.
        var principal = new com.machineguard.platform.iam.infrastructure.security.IamPrincipal(
                UUID.randomUUID(), ORG, Role.VIEWER, UUID.randomUUID(), NOW.toInstant());
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_UNSUPPORTED")));
        for (String path : new String[] {BASE, BASE + "/" + pending.getId()})
            mvc.perform(get(path).with(request -> {
                var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                new org.springframework.security.web.context.RequestAttributeSecurityContextRepository()
                        .saveContext(context, request, new org.springframework.mock.web.MockHttpServletResponse());
                return request;
            })).andExpect(status().isForbidden());
    }

    @Test void queryingDoesNotChangeAlertOrIncidentState() throws Exception {
        mvc.perform(get(BASE + "/" + acknowledged.getId()).header("Authorization", auth(ORG, Role.VIEWER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedAt").value(NOW.plusMinutes(3).toInstant().toString()));
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM incidents WHERE id=?", String.class, acknowledged.getIncidentId()));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM alerts WHERE id=?", String.class, pending.getId()));
    }

    @Test void swaggerExposesOnlyGetOperationsForAlerts() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/alerts'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/alerts/{alertId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/alerts'].post").doesNotExist());
    }
}
