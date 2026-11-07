package com.machineguard.platform.customeracquisition;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.infrastructure.security.JwtTokenService;
import com.machineguard.platform.support.SessionFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Operator approval with a configured synthetic key (SHA-256 of mg-test-operator-key-not-for-production-2026). */
@SpringBootTest(properties={
        "spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:pilotoperator;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}",
        "machineguard.iam.operator-key-hash=3bdc694bfe022a50902353150f853db880248010ff2d0d407b846ebfdc1a4213",
        "machineguard.acquisition.pilot-ip-limit=1000"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PilotOperatorApiIntegrationTest {
    static final String OPERATOR_KEY = "mg-test-operator-key-not-for-production-2026";
    static final String PASSWORD = "landing pilot password 2026";
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;

    static String email() { return "operator-" + UUID.randomUUID() + "@landing.example.test"; }

    MvcResult send(MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder).andReturn();
    }

    MockHttpServletRequestBuilder withKey(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Operator-Key", OPERATOR_KEY);
    }

    JsonNode json(MvcResult result) throws Exception {
        String text = result.getResponse().getContentAsString();
        return text.isEmpty() ? json.nullNode() : json.readTree(text);
    }

    UUID submit(String email, String company) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("companyName", company);
        body.put("contactName", "Ana Perez");
        body.put("contactEmail", email);
        body.put("contactPhone", "+51 987 654 321");
        body.put("industry", "CHEMICAL");
        body.put("estimatedMonthlyLoss", 800);
        MvcResult result = send(post("/api/v1/pilot-requests").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
        assertEquals(202, result.getResponse().getStatus());
        return UUID.fromString(json(result).get("id").asText());
    }

    MvcResult approve(UUID id) throws Exception {
        return send(withKey(post("/api/v1/operator/pilot-requests/" + id + "/approve")));
    }

    int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? -1 : value;
    }

    /** Pending requests reference organizations; remove them so other classes can clear organizations. */
    @AfterEach void removePilotRequests() { jdbc.update("DELETE FROM pilot_requests"); }

    @Test void operatorRoutesRejectMissingWrongAndUserCredentialsWithoutSideEffects() throws Exception {
        UUID id = submit(email(), "Sin clave SAC");
        assertEquals(401, send(get("/api/v1/operator/pilot-requests")).getResponse().getStatus());
        assertEquals(401, send(get("/api/v1/operator/pilot-requests").header("X-Operator-Key", "wrong-key"))
                .getResponse().getStatus());
        assertEquals(401, send(post("/api/v1/operator/pilot-requests/" + id + "/approve")).getResponse().getStatus());
        assertEquals(401, send(post("/api/v1/operator/pilot-requests/" + id + "/approve")
                .header("X-Operator-Key", "wrong-key")).getResponse().getStatus());

        UUID admin = UUID.randomUUID();
        String bearer = "Bearer " + SessionFixtures.token(jdbc, tokens, admin, UUID.randomUUID(), Role.ADMIN, null);
        assertEquals(401, send(get("/api/v1/operator/pilot-requests").header("Authorization", bearer))
                .getResponse().getStatus(), "an administrator JWT is not an operator credential");
        assertEquals(401, send(withKey(post("/api/v1/operator/pilot-requests/" + id + "/approve")
                .header("Authorization", bearer))).getResponse().getStatus(), "one credential type per request");

        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM pilot_requests WHERE id=?", String.class, id));
        assertEquals(0, count("SELECT count(*) FROM organizations WHERE acquisition_request_id=?", id));
    }

    @Test void listsPendingRequestsWithoutSecretsAndRejectsUnknownStatuses() throws Exception {
        String email = email();
        UUID id = submit(email, "Lista SAC");
        JsonNode listed = json(send(withKey(get("/api/v1/operator/pilot-requests?status=PENDING"))));
        boolean found = false;
        for (JsonNode item : listed) {
            assertFalse(item.has("invitationToken"));
            assertFalse(item.has("passwordHash"));
            if (id.toString().equals(item.get("id").asText())) {
                found = true;
                assertEquals(email, item.get("contactEmail").asText());
                assertEquals("PENDING", item.get("status").asText());
                assertEquals("CHEMICAL", item.get("industry").asText());
            }
        }
        assertTrue(found, "pending request must be listed");
        assertEquals(400, send(withKey(get("/api/v1/operator/pilot-requests?status=MINE"))).getResponse().getStatus());
    }

    @Test void firstApprovalCreatesOneActiveOrganizationInactiveAdministratorAndOneTimeInvitation() throws Exception {
        String email = email();
        UUID id = submit(email, "Aprobada Norte SAC");
        MvcResult result = approve(id);
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        JsonNode body = json(result);
        assertEquals("APPROVED", body.get("status").asText());
        assertTrue(body.get("invitationIssuedNow").asBoolean());
        String token = body.get("invitationToken").asText();
        assertTrue(token.length() >= 40);
        Instant expires = Instant.parse(body.get("invitationExpiresAt").asText());
        Duration untilExpiry = Duration.between(Instant.now(), expires);
        assertTrue(untilExpiry.toHours() >= 23 && untilExpiry.toHours() <= 24, "invitation TTL is 24 hours");
        UUID organizationId = UUID.fromString(body.get("organizationId").asText());

        assertEquals(1, count("SELECT count(*) FROM organizations WHERE acquisition_request_id=? AND status='ACTIVE' "
                + "AND name='Aprobada Norte SAC'", id));
        assertEquals("INACTIVE", jdbc.queryForObject("SELECT status FROM users WHERE email=? AND role='ADMIN' AND organization_id=?",
                String.class, email, organizationId));
        assertEquals(1, count("SELECT count(*) FROM initial_admin_invitations i JOIN users u ON u.id = i.user_id "
                + "WHERE u.email=? AND i.token_hash=? AND i.consumed_at IS NULL", email, JwtTokenService.tokenHash(token)));
        assertEquals(0, count("SELECT count(*) FROM initial_admin_invitations WHERE token_hash=?", token),
                "the raw invitation token must never be stored");
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM pilot_requests WHERE id=?", String.class, id));
        assertEquals(organizationId, jdbc.queryForObject("SELECT organization_id FROM pilot_requests WHERE id=?", UUID.class, id));
    }

    @Test void repeatedApprovalIsIdempotentAndNeverReissuesTheToken() throws Exception {
        String email = email();
        UUID id = submit(email, "Doble aprobacion SAC");
        JsonNode first = json(approve(id));
        JsonNode second = json(approve(id));
        assertTrue(first.get("invitationIssuedNow").asBoolean());
        assertFalse(second.get("invitationIssuedNow").asBoolean());
        assertTrue(second.get("invitationToken").isNull());
        assertEquals(first.get("organizationId").asText(), second.get("organizationId").asText());
        assertEquals(1, count("SELECT count(*) FROM organizations WHERE acquisition_request_id=?", id));
        assertEquals(1, count("SELECT count(*) FROM users WHERE email=?", email));
    }

    @Test void invitationFromApprovalActivatesTheAdministratorThroughTheExistingAuthApi() throws Exception {
        String email = email();
        UUID id = submit(email, "Activacion SAC");
        String token = json(approve(id)).get("invitationToken").asText();

        Map<String, Object> accept = Map.of("token", token, "password", PASSWORD);
        assertEquals(201, send(post("/api/v1/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(accept))).getResponse().getStatus());
        Map<String, Object> login = Map.of("email", email, "password", PASSWORD);
        MvcResult signedIn = send(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(login)));
        assertEquals(200, signedIn.getResponse().getStatus());
        JsonNode session = json(signedIn);
        assertEquals("ADMIN", session.get("user").get("role").asText());
        assertEquals("ACTIVE", session.get("organization").get("status").asText());
        assertEquals(401, send(post("/api/v1/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(accept))).getResponse().getStatus(), "invitation is single-use");
    }

    @Test void rejectionIsIdempotentBlocksLaterApprovalAndAllowsANewSubmission() throws Exception {
        String email = email();
        UUID id = submit(email, "Rechazada SAC");
        MvcResult rejected = send(withKey(post("/api/v1/operator/pilot-requests/" + id + "/reject")));
        assertEquals(200, rejected.getResponse().getStatus());
        assertEquals("REJECTED", json(rejected).get("status").asText());
        assertEquals(200, send(withKey(post("/api/v1/operator/pilot-requests/" + id + "/reject"))).getResponse().getStatus());
        assertEquals(409, approve(id).getResponse().getStatus());
        assertEquals(0, count("SELECT count(*) FROM organizations WHERE acquisition_request_id=?", id));
        assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM pilot_requests WHERE id=?", String.class, id));

        UUID later = submit(email, "Rechazada SAC");
        assertNotEquals(id, later, "a rejected email may submit a new pending request");
    }

    @Test void approvedRequestCannotBeRejected() throws Exception {
        UUID id = submit(email(), "Aprobada luego SAC");
        assertEquals(200, approve(id).getResponse().getStatus());
        assertEquals(409, send(withKey(post("/api/v1/operator/pilot-requests/" + id + "/reject"))).getResponse().getStatus());
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM pilot_requests WHERE id=?", String.class, id));
    }

    @Test void approvalFailsWithoutSideEffectsWhenTheContactEmailAlreadyHasAUser() throws Exception {
        String email = email();
        UUID first = submit(email, "Primera SAC");
        assertEquals(200, approve(first).getResponse().getStatus());
        UUID second = submit(email, "Segunda SAC");
        assertNotEquals(first, second);
        MvcResult conflict = approve(second);
        assertEquals(409, conflict.getResponse().getStatus());
        assertEquals("Pilot contact email is already registered", json(conflict).get("message").asText());
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM pilot_requests WHERE id=?", String.class, second));
        assertEquals(0, count("SELECT count(*) FROM organizations WHERE acquisition_request_id=?", second));
        assertEquals(1, count("SELECT count(*) FROM users WHERE email=?", email));
    }

    @Test void unknownRequestsReturnNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        assertEquals(404, approve(unknown).getResponse().getStatus());
        assertEquals(404, send(withKey(post("/api/v1/operator/pilot-requests/" + unknown + "/reject"))).getResponse().getStatus());
    }

    @Test void operatorKeyIsNotAHumanSessionForOtherWriteRoutes() throws Exception {
        assertEquals(401, send(post("/api/v1/gateways").header("X-Operator-Key", OPERATOR_KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).getResponse().getStatus());
    }
}
