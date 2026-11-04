package com.machineguard.platform.environmentalmonitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:gatewayingestion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}")
@AutoConfigureMockMvc
class GatewayIngestionApiIntegrationTest {
    static final Instant NOW=Instant.parse("2026-10-30T12:00:00Z");
    static final UUID ORG=new UUID(0,1901),OTHER=new UUID(0,1902),ADMIN=new UUID(0,1903);
    @TestConfiguration static class Configuration {
        @Bean @Primary Clock ingestionClock() { return Clock.fixed(NOW,ZoneOffset.UTC); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizationJpaRepository organizations;
    @Autowired UserJpaRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtTokenService tokens;
    record Device(UUID zone,UUID point,UUID sensor,String code) { }
    Device first,second,foreign;
    String admin,credential,gatewayId;

    @BeforeEach void setup() throws Exception {
        for (String table:List.of("traceability_report_excursions","traceability_reports","non_conformities","excursions",
                "corrective_actions","alerts","incidents","gateway_sensor_nodes","gateway_credentials","measurements",
                "thresholds","sensor_nodes","monitoring_points","monitoring_zones","initial_admin_invitations",
                "authentication_refresh_token_history","authentication_sessions","users","organizations")) jdbc.update("DELETE FROM "+table);
        organizations.save(new OrganizationEntity(ORG,"North",SubscriptionPlan.FREE,OrganizationStatus.ACTIVE,null,NOW));
        organizations.save(new OrganizationEntity(OTHER,"South",SubscriptionPlan.FREE,OrganizationStatus.ACTIVE,null,NOW));
        users.save(new UserEntity(ADMIN,ORG,"admin@ingestion.test",passwords.encode("gateway ingestion password"),"Admin",Role.ADMIN,UserStatus.ACTIVE,NOW));
        admin="Bearer "+tokens.issueAccessToken(ADMIN,ORG,Role.ADMIN,UUID.randomUUID()).value();
        first=device(ORG,"MG-01"); second=device(ORG,"MG-02"); foreign=device(OTHER,"MG-FOREIGN");
        provision(Set.of(first.sensor()));
    }
    Device device(UUID org,String code) {
        UUID zone=UUID.randomUUID(),point=UUID.randomUUID(),sensor=UUID.randomUUID();
        var at=OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC);
        jdbc.update("INSERT INTO monitoring_zones(id,organization_id,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)",zone,org,code,at,at);
        jdbc.update("INSERT INTO monitoring_points(id,monitoring_zone_id,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)",point,zone,"Point",at,at);
        jdbc.update("INSERT INTO sensor_nodes(id,monitoring_point_id,device_code,sampling_interval_seconds,status,created_at) VALUES (?,?,?,60,'OFFLINE',?)",sensor,point,code,at);
        for (String variable:List.of("TEMPERATURE","HUMIDITY")) {
            jdbc.update("INSERT INTO thresholds(id,monitoring_zone_id,environmental_variable,minimum_value,maximum_value,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID(),zone,variable,variable.equals("TEMPERATURE")?2:30,variable.equals("TEMPERATURE")?8:75,at,at);
        }
        return new Device(zone,point,sensor,code);
    }
    void provision(Set<UUID> sensors) throws Exception {
        var issued=human(post("/api/v1/gateways"),Map.of("name","Test gateway","sensorNodeIds",sensors),201);
        gatewayId=issued.get("gateway").get("id").asText(); credential=issued.get("credential").asText();
    }
    JsonNode result(MockHttpServletRequestBuilder request,Object body,int expected) throws Exception {
        if (body!=null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult result=mvc.perform(request).andExpect(status().is(expected)).andReturn();
        String text=result.getResponse().getContentAsString(); return text.isEmpty()?json.nullNode():json.readTree(text);
    }
    JsonNode human(MockHttpServletRequestBuilder request,Object body,int expected) throws Exception {
        return result(request.header("Authorization",admin),body,expected);
    }
    JsonNode ingest(String route,Object body,int expected) throws Exception {
        return result(post(route).header("X-Device-Credential",credential),body,expected);
    }
    Map<String,Object> reading(Device device,int ago,int temperature) {
        return new HashMap<>(Map.of("deviceCode",device.code(),"temperature",temperature,"humidity",50,
                "capturedAt",NOW.minusSeconds(ago).toString()));
    }
    long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class); }
    List<UUID> measurementIds() { return jdbc.queryForList("SELECT id FROM measurements ORDER BY id",UUID.class); }

    @Test void oneReadingCreatesTwoVariablesAndAnIdenticalRetryConfirmsWithoutDuplicateEvents() throws Exception {
        var body=reading(first,600,11);
        JsonNode accepted=ingest("/api/v1/measurements",body,201);
        assertEquals(2,accepted.get("measurementsCreated").asInt()); assertEquals(1,count("incidents"));
        var ids=measurementIds();
        JsonNode repeated=ingest("/api/v1/measurements",body,200);
        assertEquals(0,repeated.get("measurementsCreated").asInt()); assertEquals(2,repeated.get("measurementsReused").asInt());
        assertEquals(ids,measurementIds()); assertEquals(1,count("alerts")); assertEquals(1,count("excursions"));
        assertEquals(1,human(get("/api/v1/alerts"),null,200).size());
    }

    @Test void ingestionErrorsHaveStableCodesAndKeepAtomicity() throws Exception {
        assertEquals("NODE_NOT_AUTHORIZED",ingest("/api/v1/measurements",reading(second,600,11),404).path("code").asText());
        assertEquals("READING_TIME_INVALID",ingest("/api/v1/measurements",reading(first,-301,11),400).path("code").asText());
        var invalid=reading(first,600,11); invalid.put("temperature","not-a-number");
        assertEquals("READING_INVALID",ingest("/api/v1/measurements",invalid,400).path("code").asText());
        ingest("/api/v1/measurements",reading(first,600,11),201);
        assertEquals("READING_CONFLICT",ingest("/api/v1/measurements",reading(first,600,12),409).path("code").asText());
        provision(Set.of(first.sensor(),second.sensor()));
        jdbc.update("DELETE FROM thresholds WHERE monitoring_zone_id=? AND environmental_variable='HUMIDITY'",second.zone());
        assertEquals("THRESHOLD_NOT_CONFIGURED",ingest("/api/v1/measurements/batch",Map.of("measurements",
                List.of(reading(first,590,11),reading(second,580,12))),409).path("code").asText());
        assertEquals(2,count("measurements"));
    }

    @Test void credentialFailuresHaveStableCodesWithoutChangingStatus() throws Exception {
        assertEquals("GATEWAY_CREDENTIAL_REJECTED",result(post("/api/v1/measurements")
                .header("X-Device-Credential","invalid"),reading(first,600,11),401).path("code").asText());
        assertEquals("GATEWAY_CREDENTIAL_REJECTED",result(post("/api/v1/measurements"),reading(first,600,11),401).path("code").asText());
        assertEquals("GATEWAY_INGEST_FORBIDDEN",human(post("/api/v1/measurements"),reading(first,600,11),403).path("code").asText());
    }

    @Test void offsetsAndMicrosecondCanonicalizationPreserveReadingIdentity() throws Exception {
        var body=reading(first,600,11);
        var captured=OffsetDateTime.ofInstant(NOW.minusSeconds(600),ZoneOffset.UTC).withNano(123456789);
        body.put("capturedAt",captured.toString()); ingest("/api/v1/measurements",body,201);
        body.put("capturedAt",captured.withNano(123456999).withOffsetSameInstant(ZoneOffset.ofHours(-5)).toString());
        ingest("/api/v1/measurements",body,200); assertEquals(2,count("measurements"));
        body.put("temperature",12); ingest("/api/v1/measurements",body,409);
        assertEquals(2,count("measurements"));
    }

    @Test void unassignedForeignUnknownAndInactiveSensorsCannotBeUsed() throws Exception {
        ingest("/api/v1/measurements",reading(second,600,11),404);
        ingest("/api/v1/measurements",reading(foreign,600,11),404);
        var unknown=reading(first,600,11); unknown.put("deviceCode","unknown"); ingest("/api/v1/measurements",unknown,404);
        jdbc.update("UPDATE sensor_nodes SET status='INACTIVE' WHERE id=?",first.sensor());
        ingest("/api/v1/measurements",reading(first,600,11),404); assertEquals(0,count("measurements"));
    }

    @Test void claimedIdentityFieldsAndHeadersCannotChangeTheGatewayBinding() throws Exception {
        var body=reading(first,600,11); body.put("organizationId",OTHER); body.put("sensorNodeId",foreign.sensor());
        body.put("monitoringZoneId",foreign.zone()); body.put("monitoringPointId",foreign.point());
        result(post("/api/v1/measurements").header("X-Device-Credential",credential).header("X-Organization-Id",OTHER),body,201);
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM measurements WHERE organization_id=? AND monitoring_zone_id=? AND sensor_node_id=?",Integer.class,ORG,first.zone(),first.sensor()));
    }

    @Test void batchesAreAtomicWhenALaterVariableCannotBeRecorded() throws Exception {
        provision(Set.of(first.sensor(),second.sensor()));
        jdbc.update("DELETE FROM thresholds WHERE monitoring_zone_id=? AND environmental_variable='HUMIDITY'",second.zone());
        ingest("/api/v1/measurements/batch",Map.of("measurements",List.of(reading(first,600,11),reading(second,590,12))),409);
        for (String table:List.of("measurements","incidents","alerts","incident_measurements","excursions")) assertEquals(0,count(table),table);
        assertEquals("OFFLINE",jdbc.queryForObject("SELECT status FROM sensor_nodes WHERE id=?",String.class,first.sensor()));
    }

    @Test void replayingAnUnconfirmedBatchConfirmsAllPreviouslyAcceptedReadings() throws Exception {
        provision(Set.of(first.sensor(),second.sensor()));
        var readings=List.of(reading(first,600,5),reading(second,550,11),reading(first,500,12));
        var firstResult=ingest("/api/v1/measurements/batch",Map.of("measurements",readings),201);
        assertEquals(6,firstResult.get("measurementsCreated").asInt()); var ids=measurementIds();
        var reversed=new ArrayList<>(readings); Collections.reverse(reversed);
        var replay=ingest("/api/v1/measurements/batch",Map.of("measurements",reversed),200);
        assertEquals(6,replay.get("measurementsReused").asInt()); assertEquals(ids,measurementIds());
        assertEquals(2,count("incidents")); assertEquals(2,count("alerts"));
    }

    @Test void conflictingReplayRollsBackEarlierBatchWritesWithoutOverwritingHistory() throws Exception {
        ingest("/api/v1/measurements",reading(first,600,11),201); var ids=measurementIds();
        ingest("/api/v1/measurements/batch",Map.of("measurements",List.of(reading(first,700,5),reading(first,600,12))),409);
        assertEquals(ids,measurementIds()); assertEquals(1,count("incidents"));
    }

    @Test void credentialTypeValidationFutureDatesAndBatchLimitsAreEnforced() throws Exception {
        result(post("/api/v1/measurements"),reading(first,600,11),401);
        human(post("/api/v1/measurements"),reading(first,600,11),403);
        result(post("/api/v1/measurements").header("X-Device-Credential","invalid"),reading(first,600,11),401);
        human(post("/api/v1/measurements").header("X-Device-Credential",credential),reading(first,600,11),401);
        ingest("/api/v1/measurements",reading(first,-301,11),400);
        ingest("/api/v1/measurements/batch",Map.of("measurements",List.of()),400);
        ingest("/api/v1/measurements/batch",Map.of("measurements",Collections.nCopies(501,reading(first,600,11))),400);
        assertEquals(0,count("measurements"));
    }

    @Test void rotationAndRevocationTakeEffectAtTheIngestionBoundary() throws Exception {
        ingest("/api/v1/measurements",reading(first,600,11),201);
        var issued=human(post("/api/v1/gateways/"+gatewayId+"/credentials"),null,200);
        ingest("/api/v1/measurements",reading(first,590,11),401);
        credential=issued.get("credential").asText(); ingest("/api/v1/measurements",reading(first,590,11),201);
        human(post("/api/v1/gateways/"+gatewayId+"/revoke"),null,200);
        ingest("/api/v1/measurements",reading(first,580,11),401); assertEquals(4,count("measurements"));
    }

    int concurrent(Object body,String route,CountDownLatch ready,CountDownLatch start) throws Exception {
        ready.countDown(); assertTrue(start.await(10,TimeUnit.SECONDS));
        return mvc.perform(post(route).header("X-Device-Credential",credential).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();
    }
    List<Integer> race(Object firstBody,Object secondBody,String route) throws Exception {
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try (var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=executor.submit(()->concurrent(firstBody,route,ready,start));
            var b=executor.submit(()->concurrent(secondBody,route,ready,start));
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            return java.util.stream.Stream.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)).sorted().toList();
        }
    }
    @Test void simultaneousRetriesCreateOnlyOnePairOfMeasurements() throws Exception {
        Object body=reading(first,600,11);
        assertEquals(List.of(200,201),race(body,body,"/api/v1/measurements"));
        assertEquals(2,count("measurements")); assertEquals(1,count("alerts"));
    }
    @Test void oppositeZoneOrderInConcurrentBatchesDoesNotDeadlockOrDuplicate() throws Exception {
        provision(Set.of(first.sensor(),second.sensor()));
        var a=reading(first,600,11); var b=reading(second,590,12);
        assertEquals(List.of(200,201),race(Map.of("measurements",List.of(a,b)),Map.of("measurements",List.of(b,a)),"/api/v1/measurements/batch"));
        assertEquals(4,count("measurements")); assertEquals(2,count("incidents"));
    }
}
