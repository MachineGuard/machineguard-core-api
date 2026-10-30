package com.machineguard.platform.iam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machineguard.platform.iam.domain.model.OrganizationStatus;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.domain.model.SubscriptionPlan;
import com.machineguard.platform.iam.domain.model.UserStatus;
import com.machineguard.platform.iam.infrastructure.persistence.InitialAdminInvitationEntity;
import com.machineguard.platform.iam.infrastructure.persistence.OrganizationEntity;
import com.machineguard.platform.iam.infrastructure.persistence.UserEntity;
import com.machineguard.platform.iam.infrastructure.persistence.repository.InitialAdminInvitationJpaRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.OrganizationJpaRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.machineguard.platform.iam.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.machineguard.platform.iam.infrastructure.security.JwtTokenService.tokenHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IamApiIntegrationTest {
    private static final UUID ORG = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_ORG = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ADMIN = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID VIEWER = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID OTHER_ADMIN = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired OrganizationJpaRepository organizations;
    @Autowired UserJpaRepository users;
    @Autowired InitialAdminInvitationJpaRepository invitations;
    @Autowired JwtTokenService tokens;

    @BeforeEach
    void prepareTenants() {
        jdbc.update("DELETE FROM initial_admin_invitations");
        jdbc.update("DELETE FROM authentication_refresh_token_history");
        jdbc.update("DELETE FROM authentication_sessions");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM organizations");
        Instant now = Instant.now();
        organizations.save(new OrganizationEntity(ORG, "North Farm", SubscriptionPlan.FREE, OrganizationStatus.ACTIVE, null, now));
        organizations.save(new OrganizationEntity(OTHER_ORG, "South Farm", SubscriptionPlan.FREE, OrganizationStatus.ACTIVE, null, now));
        users.save(new UserEntity(ADMIN, ORG, "admin@example.test", passwords.encode(PASSWORD), "Tenant Admin", Role.ADMIN, UserStatus.ACTIVE, now));
        users.save(new UserEntity(VIEWER, ORG, "viewer@example.test", passwords.encode(PASSWORD), "Tenant Viewer", Role.VIEWER, UserStatus.ACTIVE, now));
        users.save(new UserEntity(OTHER_ADMIN, OTHER_ORG, "other@example.test", passwords.encode(PASSWORD), "Other Admin", Role.ADMIN, UserStatus.ACTIVE, now));
    }

    @Test
    void loginNormalizesEmailAndTraceabilityIgnoresCallerSuppliedIdentityHeaders() throws Exception {
        JsonNode auth = login("  ADMIN@EXAMPLE.TEST ", PASSWORD);
        String access = auth.get("accessToken").asText();

        mvc.perform(withBearer(get("/api/v1/users/me"), access)
                        .header("X-Organization-Id", OTHER_ORG).header("X-User-Id", OTHER_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ADMIN.toString()))
                .andExpect(jsonPath("$.organizationId").value(ORG.toString()));

        mvc.perform(withBearer(get("/api/v1/organizations/" + ORG), access)
                        .header("X-Organization-Id", OTHER_ORG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORG.toString()));
        mvc.perform(withBearer(get("/api/v1/organizations/" + OTHER_ORG), access))
                .andExpect(status().isForbidden());
        int signature = access.lastIndexOf('.') + 1;
        // Change actual signature bits; the last Base64 character can differ only in unused padding bits.
        String tampered = access.substring(0, signature) + (access.charAt(signature) == 'A' ? 'B' : 'A')
                + access.substring(signature + 1);
        mvc.perform(withBearer(get("/api/v1/users/me"), tampered)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/traceability/excursions")
                        .header("X-Organization-Id", ORG).header("X-User-Id", ADMIN))
                .andExpect(status().isUnauthorized());
        mvc.perform(withBearer(get("/api/v1/traceability/excursions"), access)
                        .header("X-Organization-Id", OTHER_ORG).header("X-User-Id", OTHER_ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void refreshRotatesTokensAndReplayRevokesThatSession() throws Exception {
        JsonNode initial = login("admin@example.test", PASSWORD);
        String firstRefresh = initial.get("refreshToken").asText();
        JsonNode rotated = json.readTree(mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("refreshToken", firstRefresh))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String secondRefresh = rotated.get("refreshToken").asText();
        assertNotEquals(firstRefresh, secondRefresh);

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("refreshToken", firstRefresh))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("refreshToken", secondRefresh))))
                .andExpect(status().isUnauthorized());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM authentication_sessions WHERE revoked_at IS NOT NULL", Integer.class));
    }

    @Test
    void passwordChangeInvalidatesRefreshSessionsAndRequiresCurrentPassword() throws Exception {
        JsonNode auth = login("admin@example.test", PASSWORD);
        mvc.perform(withBearer(post("/api/v1/auth/password-reset"), auth.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("currentPassword", PASSWORD,
                                "newPassword", "another correct horse"))))
                .andExpect(status().isOk());
        String refresh = auth.get("refreshToken").asText();
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", "admin@example.test", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", "admin@example.test", "password", "another correct horse"))))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesRefreshSessionAndFailsTokenValidation() throws Exception {
        JsonNode auth = login("admin@example.test", PASSWORD);
        String access = auth.get("accessToken").asText();
        String refresh = auth.get("refreshToken").asText();
        mvc.perform(withBearer(post("/api/v1/auth/logout"), access).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("refreshToken", refresh))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
        mvc.perform(withBearer(post("/api/v1/auth/validate"), access)).andExpect(status().isUnauthorized());
    }

    @Test
    void lastActiveAdministratorCannotBeDemotedOrDeactivated() throws Exception {
        String access = login("admin@example.test", PASSWORD).get("accessToken").asText();
        mvc.perform(withBearer(patch("/api/v1/users/" + ADMIN + "/role"), access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isConflict());
        mvc.perform(withBearer(patch("/api/v1/users/" + ADMIN + "/status"), access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isConflict());
        assertEquals(Role.ADMIN, users.findById(ADMIN).orElseThrow().getRole());
        assertEquals(UserStatus.ACTIVE, users.findById(ADMIN).orElseThrow().getStatus());
    }

    @Test
    void concurrentDemotionsCannotRemoveEveryActiveAdministrator() throws Exception {
        UUID secondAdmin = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
        users.save(new UserEntity(secondAdmin, ORG, "second-admin@example.test", passwords.encode(PASSWORD),
                "Second Admin", Role.ADMIN, UserStatus.ACTIVE, Instant.now()));
        String firstToken = tokens.issueAccessToken(ADMIN, ORG, Role.ADMIN, UUID.randomUUID()).value();
        String secondToken = tokens.issueAccessToken(secondAdmin, ORG, Role.ADMIN, UUID.randomUUID()).value();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> demoteAtBarrier(firstToken, secondAdmin, ready, start));
            var second = executor.submit(() -> demoteAtBarrier(secondToken, ADMIN, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int firstStatus = first.get(10, TimeUnit.SECONDS);
            int secondStatus = second.get(10, TimeUnit.SECONDS);
            assertTrue(firstStatus == 200 || firstStatus == 403 || firstStatus == 409);
            assertTrue(secondStatus == 200 || secondStatus == 403 || secondStatus == 409);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE organization_id = ? AND role = 'ADMIN' AND status = 'ACTIVE'",
                    Integer.class, ORG));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void onlyAdminsCanCreateUsersAndNormalizedEmailsAreUniqueAcrossTenants() throws Exception {
        String viewerToken = login("viewer@example.test", PASSWORD).get("accessToken").asText();
        mvc.perform(withBearer(post("/api/v1/organizations/" + ORG + "/users"), viewerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email":"new@example.test","password":"long enough password","fullName":"New Viewer","role":"VIEWER"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(withBearer(post("/api/v1/traceability/reports"), viewerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"monitoringZoneId":"99999999-9999-4999-8999-999999999999",
                                 "periodStart":"2026-09-01T00:00:00Z","periodEnd":"2026-09-02T00:00:00Z"}
                                """))
                .andExpect(status().isForbidden());

        String adminToken = login("admin@example.test", PASSWORD).get("accessToken").asText();
        mvc.perform(withBearer(post("/api/v1/organizations/" + OTHER_ORG + "/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email":"new@example.test","password":"long enough password","fullName":"New Viewer","role":"VIEWER"}
                                """))
                .andExpect(status().isForbidden());
        mvc.perform(withBearer(post("/api/v1/organizations/" + ORG + "/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email":"VIEWER@EXAMPLE.TEST","password":"long enough password","fullName":"Duplicate","role":"VIEWER"}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(withBearer(post("/api/v1/organizations/" + ORG + "/users"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", "unicode@example.test",
                                "password", "界".repeat(25), "fullName", "Unicode Test", "role", "VIEWER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invitationTokenCanOnlyBeConsumedOnce() throws Exception {
        Instant now = Instant.now();
        UUID invitedUserId = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
        UserEntity invited = users.save(new UserEntity(invitedUserId, ORG, "invited@example.test",
                passwords.encode(tokens.newRefreshToken()), "Invited Admin", Role.ADMIN, UserStatus.INACTIVE, now));
        assertEquals(invitedUserId, invited.getId());
        String inviteToken = tokens.newInvitationToken();
        UUID invitationId = UUID.randomUUID();
        invitations.save(new InitialAdminInvitationEntity(invitationId, invitedUserId, tokenHash(inviteToken), now, now.plusSeconds(3600)));

        mvc.perform(post("/api/v1/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("token", inviteToken, "password", "first secure password"))))
                .andExpect(status().isCreated());
        assertEquals(UserStatus.ACTIVE, users.findById(invitedUserId).orElseThrow().getStatus());
        mvc.perform(post("/api/v1/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("token", inviteToken, "password", "second secure password"))))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode login(String email, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private int demoteAtBarrier(String actorToken, UUID targetId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("concurrency test did not start");
        return mvc.perform(withBearer(patch("/api/v1/users/" + targetId + "/role"), actorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}"))
                .andReturn().getResponse().getStatus();
    }
}
