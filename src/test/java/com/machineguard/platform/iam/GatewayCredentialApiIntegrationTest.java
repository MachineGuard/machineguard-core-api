package com.machineguard.platform.iam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machineguard.platform.iam.application.GatewayAuthenticationException;
import com.machineguard.platform.iam.application.GatewayCredentialService;
import com.machineguard.platform.iam.domain.model.*;
import com.machineguard.platform.iam.infrastructure.persistence.OrganizationEntity;
import com.machineguard.platform.iam.infrastructure.persistence.UserEntity;
import com.machineguard.platform.iam.infrastructure.persistence.repository.OrganizationJpaRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.machineguard.platform.iam.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:gatewayiam;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}")
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class GatewayCredentialApiIntegrationTest {
    static final Instant NOW=Instant.parse("2026-10-30T12:00:00Z");
    static final UUID ORG=new UUID(0,901), OTHER=new UUID(0,902), ADMIN=new UUID(0,903);
    static final UUID SENSOR=new UUID(0,911), UNASSIGNED=new UUID(0,912), FOREIGN_SENSOR=new UUID(0,913);
    static final String PASSWORD="gateway integration password";
    @TestConfiguration static class Configuration {
        @Bean @Primary Clock gatewayClock() { return Clock.fixed(NOW,ZoneOffset.UTC); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizationJpaRepository organizations;
    @Autowired UserJpaRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired GatewayCredentialService gateways;
    String admin,viewer,other;
    @BeforeEach void setup() throws Exception {
        for (String table:List.of("gateway_sensor_nodes","gateway_credentials","measurements","thresholds","sensor_nodes",
                "monitoring_points","monitoring_zones","initial_admin_invitations","authentication_refresh_token_history",
                "authentication_sessions","users","organizations")) jdbc.update("DELETE FROM "+table);
        organizations.save(new OrganizationEntity(ORG,"North",SubscriptionPlan.FREE,OrganizationStatus.ACTIVE,null,NOW));
        organizations.save(new OrganizationEntity(OTHER,"South",SubscriptionPlan.FREE,OrganizationStatus.ACTIVE,null,NOW));
        String hash=passwords.encode(PASSWORD);
        users.save(new UserEntity(ADMIN,ORG,"admin@gateway.test",hash,"Admin",Role.ADMIN,UserStatus.ACTIVE,NOW));
        users.save(new UserEntity(new UUID(0,904),ORG,"viewer@gateway.test",hash,"Viewer",Role.VIEWER,UserStatus.ACTIVE,NOW));
        users.save(new UserEntity(new UUID(0,905),OTHER,"other@gateway.test",hash,"Other",Role.ADMIN,UserStatus.ACTIVE,NOW));
        sensor(SENSOR,ORG,"MG-A1"); sensor(UNASSIGNED,ORG,"MG-A2"); sensor(FOREIGN_SENSOR,OTHER,"MG-B1");
        admin=login("admin@gateway.test"); viewer=login("viewer@gateway.test"); other=login("other@gateway.test");
    }
    void sensor(UUID sensor,UUID org,String code) {
        UUID zone=UUID.randomUUID(),point=UUID.randomUUID(); var at=OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC);
        jdbc.update("INSERT INTO monitoring_zones(id,organization_id,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)",zone,org,"Zone",at,at);
        jdbc.update("INSERT INTO monitoring_points(id,monitoring_zone_id,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)",point,zone,"Point",at,at);
        jdbc.update("INSERT INTO sensor_nodes(id,monitoring_point_id,device_code,sampling_interval_seconds,status,created_at) VALUES (?,?,?,60,'OFFLINE',?)",sensor,point,code,at);
    }
    JsonNode send(MockHttpServletRequestBuilder request,String bearer,Object body,int status) throws Exception {
        if (bearer!=null) request.header("Authorization",bearer);
        if (body!=null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult result=mvc.perform(request).andExpect(status().is(status)).andReturn();
        String text=result.getResponse().getContentAsString();
        return text.isEmpty()?json.nullNode():json.readTree(text);
    }
    String login(String email) throws Exception {
        return "Bearer "+send(post("/api/v1/auth/login"),null,Map.of("email",email,"password",PASSWORD),200).get("accessToken").asText();
    }
    JsonNode register() throws Exception {
        return send(post("/api/v1/gateways"),admin,Map.of("name","Warehouse gateway","sensorNodeIds",List.of(SENSOR)),201);
    }

    @Test void registrationReturnsTheSecretOnceAndPersistsOnlyItsHash() throws Exception {
        JsonNode issued=register(); String id=issued.get("gateway").get("id").asText(),raw=issued.get("credential").asText();
        assertTrue(raw.startsWith("mgg_"+id+"."));
        assertEquals(JwtTokenService.tokenHash(raw),jdbc.queryForObject("SELECT credential_hash FROM gateway_credentials WHERE id=?",String.class,UUID.fromString(id)));
        var identity=gateways.authenticate(raw);
        assertEquals(ORG,identity.organizationId()); assertEquals(Set.of(SENSOR),identity.sensorNodeIds());
        JsonNode detail=send(get("/api/v1/gateways/"+id),admin,null,200);
        assertFalse(detail.has("credential")); assertFalse(detail.has("credentialHash"));
        assertEquals(ADMIN.toString(),detail.get("createdBy").asText());
        assertEquals(1,send(get("/api/v1/gateways"),admin,null,200).size());
        assertEquals(0,send(get("/api/v1/gateways").header("X-Organization-Id",ORG),other,null,200).size());
        send(get("/api/v1/gateways/"+id),other,null,404);
    }
    @Test void provisioningChecksCurrentAdminRoleAndSensorOwnership() throws Exception {
        Object valid=Map.of("name","Gateway","sensorNodeIds",List.of(SENSOR));
        send(post("/api/v1/gateways"),null,valid,401); send(post("/api/v1/gateways"),viewer,valid,403);
        send(post("/api/v1/gateways"),admin,Map.of("name","Foreign","sensorNodeIds",List.of(FOREIGN_SENSOR)),404);
        send(post("/api/v1/gateways"),admin,Map.of("name","Unknown","sensorNodeIds",List.of(UUID.randomUUID())),404);
        for (int ttl:List.of(0,91)) send(post("/api/v1/gateways"),admin,Map.of("name","Invalid TTL","sensorNodeIds",List.of(SENSOR),"credentialTtlDays",ttl),400);
        send(post("/api/v1/gateways"),admin,Map.of("name","Empty","sensorNodeIds",List.of()),400);
        jdbc.update("UPDATE users SET role='VIEWER' WHERE id=?",ADMIN);
        // The stale ADMIN claim is now rejected at the session boundary before authorization.
        send(post("/api/v1/gateways"),admin,valid,401);
    }
    @Test void rotationInvalidatesTheOldSecretAndRevocationCannotBeUndoneByRotation() throws Exception {
        JsonNode first=register(); String id=first.get("gateway").get("id").asText(),old=first.get("credential").asText();
        JsonNode rotated=send(post("/api/v1/gateways/"+id+"/credentials"),admin,null,200);
        String next=rotated.get("credential").asText(); assertNotEquals(old,next);
        assertThrows(GatewayAuthenticationException.class,()->gateways.authenticate(old));
        assertEquals(UUID.fromString(id),gateways.authenticate(next).gatewayId());
        JsonNode revoked=send(post("/api/v1/gateways/"+id+"/revoke"),admin,null,200);
        assertEquals(NOW.toString(),revoked.get("revokedAt").asText());
        assertThrows(GatewayAuthenticationException.class,()->gateways.authenticate(next));
        send(post("/api/v1/gateways/"+id+"/revoke"),admin,null,200);
        send(post("/api/v1/gateways/"+id+"/credentials"),admin,null,409);
        send(post("/api/v1/gateways/"+id+"/credentials"),other,null,404);
    }
    @Test void expirationAndSuspendedOrganizationsCannotAuthenticate() throws Exception {
        JsonNode expired=register(); UUID id=UUID.fromString(expired.get("gateway").get("id").asText());
        jdbc.update("UPDATE gateway_credentials SET expires_at=? WHERE id=?",OffsetDateTime.ofInstant(NOW.minusSeconds(1),ZoneOffset.UTC),id);
        assertThrows(GatewayAuthenticationException.class,()->gateways.authenticate(expired.get("credential").asText()));
        JsonNode active=register(); jdbc.update("UPDATE organizations SET status='SUSPENDED' WHERE id=?",ORG);
        assertThrows(GatewayAuthenticationException.class,()->gateways.authenticate(active.get("credential").asText()));
        send(get("/api/v1/gateways"),admin,null,403);
    }
    @Test void machineCredentialsAreRejectedOnHumanApisAndCannotBeMixedWithBearer() throws Exception {
        String credential=register().get("credential").asText();
        for (String route:List.of("/api/v1/users/me","/api/v1/gateways","/api/v1/incidents","/api/v1/environmental-monitoring/zones"))
            send(get(route).header("X-Device-Credential",credential),null,null,403);
        send(get("/api/v1/users/me").header("X-Device-Credential",credential),admin,null,401);
        send(get("/api/v1/users/me").header("X-Device-Credential","invalid"),null,null,401);
        send(post("/api/v1/measurements"),admin,Map.of(),403);
        for (String malformed:List.of("","mgg_invalid.value","mgg_"+UUID.randomUUID()+".random"))
            assertThrows(GatewayAuthenticationException.class,()->gateways.authenticate(malformed));
    }
}
