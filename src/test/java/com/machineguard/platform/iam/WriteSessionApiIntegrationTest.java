package com.machineguard.platform.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:writesession;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}")
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WriteSessionApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired com.machineguard.platform.iam.infrastructure.security.JwtTokenService tokens;
    @org.springframework.beans.factory.annotation.Value("${machineguard.iam.jwt-secret}") String signingKey;
    String access,refresh;UUID user,organization;
    @BeforeEach void login() throws Exception {
        if(jdbc.queryForObject("SELECT count(*) FROM users WHERE email='write@f5.invalid'",Integer.class)==0) {
            UUID org=UUID.randomUUID(),owner=UUID.randomUUID();var at=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
            jdbc.update("INSERT INTO organizations(id,name,subscription_plan,status,created_at,updated_at) VALUES (?,'Write tests','FREE','ACTIVE',?,?)",org,at,at);
            jdbc.update("INSERT INTO users(id,organization_id,email,password_hash,full_name,role,status,created_at,updated_at) VALUES (?,?,'write@f5.invalid',?,'Synthetic administrator','ADMIN','ACTIVE',?,?)",
                    owner,org,passwords.encode("synthetic write password"),at,at);
        }
        var body=mvc.perform(post("/api/v1/auth/login").with(r->{r.setRemoteAddr("127.0.0.2");return r;})
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"write@f5.invalid\",\"password\":\"synthetic write password\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var login=json.readTree(body);access=login.path("accessToken").asText();refresh=login.path("refreshToken").asText();
        user=UUID.fromString(login.path("user").path("id").asText());organization=UUID.fromString(login.path("organization").path("id").asText());
    }
    @Test void revokedSessionCannotConfigureMonitoringWithUnexpiredJwt() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+access).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken",refresh)))).andExpect(status().isNoContent());
        long before=jdbc.queryForObject("SELECT count(*) FROM monitoring_zones",Long.class);
        mvc.perform(post("/api/v1/environmental-monitoring/zones").header("Authorization","Bearer "+access)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Must not exist\"}")).andExpect(status().isUnauthorized());
        assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM monitoring_zones",Long.class));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"missing-session","missing-sid-claim","expired-jwt","expired-session","inactive-user","suspended-organization","changed-role","foreign-tenant","foreign-session"})
    void invalidCurrentIdentityCannotWrite(String condition) throws Exception {
        String token=access;var sid=tokens.validate(access).sessionId();
        switch(condition) {
            case "missing-session" -> token=tokens.issueAccessToken(user,organization,com.machineguard.platform.iam.domain.model.Role.ADMIN,UUID.randomUUID()).value();
            case "missing-sid-claim", "expired-jwt" -> {
                String[] parts=access.split("\\.");
                var claims=json.readTree(Base64.getUrlDecoder().decode(parts[1]));
                if(condition.equals("missing-sid-claim"))((com.fasterxml.jackson.databind.node.ObjectNode)claims).remove("sid");
                else ((com.fasterxml.jackson.databind.node.ObjectNode)claims).put("exp",java.time.Instant.now().minusSeconds(1).getEpochSecond());
                String signed=parts[0]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(claims));
                var mac=javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(signingKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
                token=signed+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signed.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            }
            case "expired-session" -> jdbc.update("UPDATE authentication_sessions SET issued_at=?,expires_at=? WHERE id=?",java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(20),java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1),sid);
            case "inactive-user" -> jdbc.update("UPDATE users SET status='INACTIVE' WHERE id=?",user);
            case "suspended-organization" -> jdbc.update("UPDATE organizations SET status='SUSPENDED' WHERE id=?",organization);
            case "changed-role" -> jdbc.update("UPDATE users SET role='VIEWER' WHERE id=?",user);
            case "foreign-tenant" -> token=tokens.issueAccessToken(user,UUID.randomUUID(),com.machineguard.platform.iam.domain.model.Role.ADMIN,sid).value();
            case "foreign-session" -> {
                UUID other=UUID.randomUUID();
                String foreign=com.machineguard.platform.support.SessionFixtures.token(jdbc,tokens,other,organization,com.machineguard.platform.iam.domain.model.Role.VIEWER,UUID.randomUUID());
                token=tokens.issueAccessToken(user,organization,com.machineguard.platform.iam.domain.model.Role.ADMIN,tokens.validate(foreign).sessionId()).value();
            }
        }
        long before=jdbc.queryForObject("SELECT count(*) FROM monitoring_zones",Long.class);
        try {
            mvc.perform(post("/api/v1/environmental-monitoring/zones").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rejected write\"}")).andExpect(status().isUnauthorized());
            assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM monitoring_zones",Long.class));
        } finally {
            jdbc.update("UPDATE users SET status='ACTIVE',role='ADMIN' WHERE id=?",user);
            jdbc.update("UPDATE organizations SET status='ACTIVE' WHERE id=?",organization);
        }
    }

}
