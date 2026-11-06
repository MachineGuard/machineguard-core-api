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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.event.*;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.SafeRangeRestored;
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
@RecordApplicationEvents
class GatewayIngestionApiIntegrationTest {
    static final Instant NOW=Instant.parse("2026-10-30T12:00:00Z");
    static final UUID ORG=new UUID(0,1901),OTHER=new UUID(0,1902),ADMIN=new UUID(0,1903);
    static Instant current=NOW;
    @TestConfiguration static class Configuration {
        @Bean @Primary Clock ingestionClock() { return new Clock() { public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;} public Instant instant(){return current;} }; }
    }
    @Autowired MockMvc mvc;
    @Autowired ApplicationEvents events;
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
        current=NOW;
        for (String table:List.of("traceability_report_excursions","traceability_reports","non_conformities","excursions",
                "corrective_actions","alerts","incidents","gateway_sensor_nodes","gateway_credentials","measurements",
                "thresholds","sensor_nodes","monitoring_points","monitoring_zones","initial_admin_invitations",
                "authentication_refresh_token_history","authentication_sessions","users","organizations")) jdbc.update("DELETE FROM "+table);
        organizations.save(new OrganizationEntity(ORG,"North",SubscriptionPlan.FREE,OrganizationStatus.ACTIVE,null,NOW));
        organizations.save(new OrganizationEntity(OTHER,"South",SubscriptionPlan.FREE,OrganizationStatus.ACTIVE,null,NOW));
        users.save(new UserEntity(ADMIN,ORG,"admin@ingestion.test",passwords.encode("gateway ingestion password"),"Admin",Role.ADMIN,UserStatus.ACTIVE,NOW));
        admin="Bearer "+com.machineguard.platform.support.SessionFixtures.token(jdbc,tokens,ADMIN,ORG,Role.ADMIN,UUID.randomUUID());
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
    String actionableIncident() throws Exception {
        ingest("/api/v1/measurements",reading(first,10,11),201);
        String id=human(get("/api/v1/alerts"),null,200).get(0).path("incidentId").asText();
        human(post("/api/v1/incidents/"+id+"/corrective-actions"),Map.of("description","Restore cooling"),201);
        return id;
    }
    @Test void freshnessExpiresWithoutQueryWritesAndResolutionRevalidates() throws Exception {
        jdbc.update("UPDATE sensor_nodes SET sampling_interval_seconds=10 WHERE id=?",first.sensor());
        String id=actionableIncident();
        ingest("/api/v1/measurements",reading(first,0,5),201);
        assertTrue(human(get("/api/v1/incidents/"+id),null,200).path("resolutionAllowed").asBoolean());
        current=NOW.plusSeconds(30);
        assertTrue(human(get("/api/v1/incidents/"+id),null,200).path("resolutionAllowed").asBoolean());
        current=NOW.plusSeconds(31);
        var detail=human(get("/api/v1/incidents/"+id),null,200);
        assertFalse(detail.path("resolutionAllowed").asBoolean());
        assertTrue(detail.path("resolutionBlockReasons").toString().contains("STALE"));
        var zone=human(get("/api/v1/environmental-monitoring/zones/"+first.zone()),null,200);
        assertEquals("STALE",zone.path("environmentalCondition").asText());
        assertEquals("OFFLINE",zone.path("sensors").get(0).path("availability").asText());
        assertEquals("ONLINE",jdbc.queryForObject("SELECT status FROM sensor_nodes WHERE id=?",String.class,first.sensor()));
        human(post("/api/v1/incidents/"+id+"/resolve"),null,409);
    }
    @Test void manualCannotForgeSourceOrBringNodeOnline() throws Exception {
        var body=new HashMap<String,Object>(Map.of("monitoringZoneId",first.zone(),"monitoringPointId",first.point(),
                "sensorNodeId",first.sensor(),"environmentalVariable","TEMPERATURE","measuredValue",5,
                "recordedAt",NOW.toString(),"source","SENSOR"));
        var recorded=human(post("/api/v1/environmental-monitoring/measurements"),body,201);
        assertEquals("MANUAL",recorded.path("source").asText());
        assertEquals("OFFLINE",jdbc.queryForObject("SELECT status FROM sensor_nodes WHERE id=?",String.class,first.sensor()));
        assertNull(jdbc.queryForObject("SELECT last_measurement_at FROM sensor_nodes WHERE id=?",OffsetDateTime.class,first.sensor()));
    }
    @Test void silentPointCannotBeRemovedFromRequiredCoverage() throws Exception {
        String id=actionableIncident();
        ingest("/api/v1/measurements",reading(first,0,5),201);
        UUID point=UUID.randomUUID(),sensor=UUID.randomUUID();var at=OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC);
        jdbc.update("INSERT INTO monitoring_points(id,monitoring_zone_id,name,status,created_at,updated_at) VALUES (?,?,'Silent','ACTIVE',?,?)",point,first.zone(),at,at);
        jdbc.update("INSERT INTO sensor_nodes(id,monitoring_point_id,device_code,sampling_interval_seconds,status,created_at) VALUES (?,?,?,10,'OFFLINE',?)",sensor,point,"silent-test",at);
        var detail=human(get("/api/v1/incidents/"+id),null,200);
        assertFalse(detail.path("resolutionAllowed").asBoolean());
        assertTrue(detail.path("resolutionBlockReasons").toString().contains("MISSING_POINT"));
        human(post("/api/v1/incidents/"+id+"/resolve"),null,409);
    }
    @Test void recentReceptionOfOldCaptureCannotAuthorizeResolution() throws Exception {
        jdbc.update("UPDATE sensor_nodes SET sampling_interval_seconds=10 WHERE id=?",first.sensor());
        String id=actionableIncident();
        ingest("/api/v1/measurements",reading(first,0,5),201);
        current=NOW.plusSeconds(60);
        ingest("/api/v1/measurements",reading(first,-1,5),201);
        var detail=human(get("/api/v1/incidents/"+id),null,200);
        assertFalse(detail.path("resolutionAllowed").asBoolean());
        human(post("/api/v1/incidents/"+id+"/resolve"),null,409);
    }
    @Test void replayKeepsSourceAndReceptionAndManualDoesNotHidePhysicalDeviation() throws Exception {
        String id=actionableIncident();
        var original=human(get("/api/v1/environmental-monitoring/measurements/latest").param("monitoringZoneId",first.zone().toString()),null,200);
        current=NOW.plusSeconds(15);
        ingest("/api/v1/measurements",reading(first,10,11),200);
        var replay=human(get("/api/v1/environmental-monitoring/measurements/latest").param("monitoringZoneId",first.zone().toString()),null,200);
        assertEquals("SENSOR",replay.get(0).path("source").asText());
        assertEquals(original.get(0).path("receivedAt"),replay.get(0).path("receivedAt"));
        human(post("/api/v1/environmental-monitoring/measurements"),Map.of("monitoringZoneId",first.zone(),
            "monitoringPointId",first.point(),"sensorNodeId",first.sensor(),"environmentalVariable","TEMPERATURE",
            "measuredValue",5,"recordedAt",NOW.toString()),201);
        assertFalse(human(get("/api/v1/incidents/"+id),null,200).path("resolutionAllowed").asBoolean());
    }

    @Test void legacyUnknownAndManualOnlyCannotCreditPhysicalCoverage() throws Exception {
        String id=actionableIncident();ingest("/api/v1/measurements",reading(first,0,5),201);
        assertEquals("SENSOR",human(get("/api/v1/environmental-monitoring/measurements/latest").param("monitoringZoneId",first.zone().toString()),null,200).get(0).path("source").asText());
        jdbc.update("UPDATE measurements SET source='LEGACY_UNKNOWN'");
        var legacy=human(get("/api/v1/incidents/"+id),null,200);
        assertFalse(legacy.path("resolutionAllowed").asBoolean());
        assertTrue(legacy.path("resolutionBlockReasons").toString().contains("LEGACY_UNKNOWN"));
        var zone=human(get("/api/v1/environmental-monitoring/zones/"+first.zone()),null,200);
        assertEquals("OFFLINE",zone.path("sensors").get(0).path("availability").asText());
        var before=jdbc.queryForObject("SELECT last_measurement_at FROM sensor_nodes WHERE id=?",OffsetDateTime.class,first.sensor());
        human(post("/api/v1/environmental-monitoring/measurements"),Map.of("monitoringZoneId",first.zone(),
            "monitoringPointId",first.point(),"sensorNodeId",first.sensor(),"environmentalVariable","TEMPERATURE",
            "measuredValue",5,"recordedAt",NOW.plusSeconds(1).toString()),201);
        var manual=human(get("/api/v1/incidents/"+id),null,200);
        assertTrue(manual.path("resolutionBlockReasons").toString().contains("MANUAL_ONLY"));
        assertEquals(before.toInstant(),jdbc.queryForObject("SELECT last_measurement_at FROM sensor_nodes WHERE id=?",OffsetDateTime.class,first.sensor()).toInstant());
        human(post("/api/v1/incidents/"+id+"/resolve"),null,409);
    }
    @Test void oneFreshEligibleSensorCoversPointAndManualDoesNotHideIt() throws Exception {
        jdbc.update("UPDATE sensor_nodes SET sampling_interval_seconds=10 WHERE id=?",first.sensor());
        String id=actionableIncident();UUID sensor=UUID.randomUUID();var at=OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC);
        jdbc.update("INSERT INTO sensor_nodes(id,monitoring_point_id,device_code,sampling_interval_seconds,status,created_at) VALUES (?,?,?,10,'OFFLINE',?)",sensor,first.point(),"alternate",at);
        provision(Set.of(first.sensor(),sensor));
        var body=reading(first,0,5);body.put("deviceCode","alternate");ingest("/api/v1/measurements",body,201);
        human(post("/api/v1/environmental-monitoring/measurements"),Map.of("monitoringZoneId",first.zone(),
            "monitoringPointId",first.point(),"sensorNodeId",first.sensor(),"environmentalVariable","TEMPERATURE",
            "measuredValue",11,"recordedAt",NOW.plusSeconds(1).toString()),201);
        current=NOW.plusSeconds(25); // First sensor's deviation is stale, alternative is still fresh.
        assertTrue(human(get("/api/v1/incidents/"+id),null,200).path("resolutionAllowed").asBoolean());
        jdbc.update("UPDATE sensor_nodes SET status='INACTIVE' WHERE id=?",sensor);
        assertFalse(human(get("/api/v1/incidents/"+id),null,200).path("resolutionAllowed").asBoolean());
        jdbc.update("UPDATE monitoring_points SET status='INACTIVE' WHERE id=?",first.point());
        assertTrue(human(get("/api/v1/incidents/"+id),null,200).path("resolutionBlockReasons").toString().contains("NO_REQUIRED_POINTS"));
    }
    @Test void resolutionUsesIncidentVariableAndRechecksThresholdChanges() throws Exception {
        String id=actionableIncident();var body=reading(first,0,5);body.put("humidity",90);ingest("/api/v1/measurements",body,201);
        assertTrue(human(get("/api/v1/incidents/"+id),null,200).path("resolutionAllowed").asBoolean());
        jdbc.update("UPDATE thresholds SET maximum_value=4 WHERE monitoring_zone_id=? AND environmental_variable='TEMPERATURE'",first.zone());
        human(post("/api/v1/incidents/"+id+"/resolve"),null,409);
        assertTrue(human(get("/api/v1/incidents/"+id),null,200).path("resolutionBlockReasons").toString().contains("OUT_OF_RANGE"));
        jdbc.update("DELETE FROM thresholds WHERE monitoring_zone_id=? AND environmental_variable='TEMPERATURE'",first.zone());
        assertTrue(human(get("/api/v1/incidents/"+id),null,200).path("resolutionBlockReasons").toString().contains("MISSING_THRESHOLD"));
        human(post("/api/v1/incidents/"+id+"/resolve"),null,409);
    }
    @Test void historicalLabelChangesButOriginalAlertMessageIsPreserved() throws Exception {
        ingest("/api/v1/measurements",reading(first,60,5),201);
        ingest("/api/v1/measurements",reading(first,120,11),201);
        var original=human(get("/api/v1/alerts"),null,200).get(0);String id=original.path("incidentId").asText();
        assertTrue(original.path("historical").asBoolean());
        String message=original.path("message").asText();
        ingest("/api/v1/measurements",reading(first,30,11),201);
        var actual=human(get("/api/v1/incidents/"+id),null,200);
        assertFalse(actual.path("incident").path("historical").asBoolean());
        assertFalse(actual.path("alert").path("historical").asBoolean());
        assertEquals(message,actual.path("alert").path("message").asText());
        assertEquals(1,count("incidents"));assertEquals(1,count("late_deviations"));
    }
    @Test void manualFutureTimestampCannotPreventPhysicalProducerUpdatingNode() throws Exception {
        human(post("/api/v1/environmental-monitoring/measurements"),Map.of("monitoringZoneId",first.zone(),
            "monitoringPointId",first.point(),"sensorNodeId",first.sensor(),"environmentalVariable","TEMPERATURE",
            "measuredValue",5,"recordedAt",NOW.plusSeconds(50).toString()),201);
        ingest("/api/v1/measurements",reading(first,0,11),201);
        assertEquals(NOW,jdbc.queryForObject("SELECT last_measurement_at FROM sensor_nodes WHERE id=?",OffsetDateTime.class,first.sensor()).toInstant());
        assertFalse(human(get("/api/v1/alerts"),null,200).get(0).path("historical").asBoolean());
    }

    long physicalRestorations() {
        return events.stream(SafeRangeRestored.class).filter(e->e.monitoringZoneId().equals(first.zone())
                && e.environmentalVariable().name().equals("TEMPERATURE")).count();
    }
    JsonNode manual(Device device,int secondsFromNow,int value) throws Exception {
        return human(post("/api/v1/environmental-monitoring/measurements"),Map.of("monitoringZoneId",device.zone(),
            "monitoringPointId",device.point(),"sensorNodeId",device.sensor(),"environmentalVariable","TEMPERATURE",
            "measuredValue",value,"recordedAt",NOW.plusSeconds(secondsFromNow).toString()),201);
    }
    Device requiredDevice(String code,UUID point) {
        var at=OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC);
        if (point==null) {
            point=UUID.randomUUID();
            jdbc.update("INSERT INTO monitoring_points(id,monitoring_zone_id,name,status,created_at,updated_at) VALUES (?,?,'Required','ACTIVE',?,?)",point,first.zone(),at,at);
        }
        UUID sensor=UUID.randomUUID();
        jdbc.update("INSERT INTO sensor_nodes(id,monitoring_point_id,device_code,sampling_interval_seconds,status,created_at) VALUES (?,?,?,10,'OFFLINE',?)",sensor,point,code,at);
        return new Device(first.zone(),point,sensor,code);
    }
    UUID openPhysicalExcursion() throws Exception {
        jdbc.update("UPDATE sensor_nodes SET sampling_interval_seconds=10 WHERE id=?",first.sensor());
        ingest("/api/v1/measurements",reading(first,3,25),201);
        String id=human(get("/api/v1/alerts"),null,200).get(0).path("incidentId").asText();
        human(post("/api/v1/incidents/"+id+"/corrective-actions"),Map.of("description","Physical recovery required"),201);
        assertEquals("ONGOING",jdbc.queryForObject("SELECT status FROM excursions",String.class));
        return jdbc.queryForObject("SELECT id FROM excursions",UUID.class);
    }
    void assertUnrecoveredPhysicalExcursion(UUID id,String reason) throws Exception {
        assertEquals(0,physicalRestorations());
        assertEquals("ONGOING",jdbc.queryForObject("SELECT status FROM excursions WHERE id=?",String.class,id));
        assertNull(jdbc.queryForObject("SELECT ended_at FROM excursions WHERE id=?",OffsetDateTime.class,id));
        String incident=human(get("/api/v1/alerts"),null,200).get(0).path("incidentId").asText();
        var detail=human(get("/api/v1/incidents/"+incident),null,200);
        assertFalse(detail.path("resolutionAllowed").asBoolean());
        assertTrue(detail.path("resolutionBlockReasons").toString().contains(reason));
    }
    void assertPhysicalRecovery(UUID id,int capturedAgo) throws Exception {
        assertEquals(1,physicalRestorations());
        assertEquals(1,count("excursions"));assertEquals(1,count("incidents"));
        assertEquals("CLOSED",jdbc.queryForObject("SELECT status FROM excursions WHERE id=?",String.class,id));
        assertEquals(NOW.minusSeconds(capturedAgo),jdbc.queryForObject("SELECT ended_at FROM excursions WHERE id=?",OffsetDateTime.class,id).toInstant());
        var event=events.stream(SafeRangeRestored.class).filter(e->e.monitoringZoneId().equals(first.zone())
                && e.environmentalVariable().name().equals("TEMPERATURE")).findFirst().orElseThrow();
        assertEquals(NOW.minusSeconds(capturedAgo),event.restoredAt().toInstant());
        assertEquals("SENSOR",jdbc.queryForObject("SELECT source FROM measurements WHERE id=?",String.class,event.measurementId()));
        String incident=human(get("/api/v1/alerts"),null,200).get(0).path("incidentId").asText();
        assertTrue(human(get("/api/v1/incidents/"+incident),null,200).path("resolutionAllowed").asBoolean());
        assertEquals("NORMAL",human(get("/api/v1/environmental-monitoring/zones/"+first.zone()),null,200).path("environmentalCondition").asText());
    }
    @ParameterizedTest @ValueSource(ints={0,50})
    void physicalRecoveryIgnoresManualNormalAndItsFutureDate(int manualSeconds) throws Exception {
        UUID excursion=openPhysicalExcursion();
        var saved=manual(first,manualSeconds,5);assertEquals("MANUAL",saved.path("source").asText());
        assertUnrecoveredPhysicalExcursion(excursion,"OUT_OF_RANGE");
        assertEquals("OUT_OF_RANGE",human(get("/api/v1/environmental-monitoring/zones/"+first.zone()),null,200).path("environmentalCondition").asText());
        ingest("/api/v1/measurements",reading(first,1,5),201);
        assertPhysicalRecovery(excursion,1);
        var receipts=jdbc.queryForList("SELECT id,received_at,source FROM measurements ORDER BY id");
        ingest("/api/v1/measurements",reading(first,1,5),200);
        assertEquals(receipts,jdbc.queryForList("SELECT id,received_at,source FROM measurements ORDER BY id"));
        assertEquals(1,physicalRestorations());assertEquals(5,count("measurements"));
    }
    @Test void physicalRecoveryIsNotHiddenByNewerManualDeviation() throws Exception {
        UUID excursion=openPhysicalExcursion();manual(first,50,25);
        assertUnrecoveredPhysicalExcursion(excursion,"OUT_OF_RANGE");
        ingest("/api/v1/measurements",reading(first,1,5),201);
        assertPhysicalRecovery(excursion,1);
    }
    @Test void physicalRecoveryWaitsForSilentRequiredPoint() throws Exception {
        UUID excursion=openPhysicalExcursion();Device silent=requiredDevice("SILENT-RECOVERY",null);
        provision(Set.of(first.sensor(),silent.sensor()));
        ingest("/api/v1/measurements",reading(first,2,5),201);
        assertUnrecoveredPhysicalExcursion(excursion,"MISSING_POINT");
        manual(silent,0,5);assertUnrecoveredPhysicalExcursion(excursion,"MISSING_POINT");
        ingest("/api/v1/measurements",reading(silent,1,5),201);
        assertPhysicalRecovery(excursion,1);
    }
    @Test void physicalRecoveryWaitsForFreshCaptureInEveryRequiredPoint() throws Exception {
        UUID excursion=openPhysicalExcursion();Device backlog=requiredDevice("BACKLOG-RECOVERY",null);
        provision(Set.of(first.sensor(),backlog.sensor()));
        ingest("/api/v1/measurements",reading(backlog,120,5),201);
        ingest("/api/v1/measurements",reading(first,2,5),201);
        assertUnrecoveredPhysicalExcursion(excursion,"STALE");
        manual(backlog,50,5);assertUnrecoveredPhysicalExcursion(excursion,"STALE");
        ingest("/api/v1/measurements",reading(backlog,1,5),201);
        assertPhysicalRecovery(excursion,1);
    }
    @Test void physicalRecoveryDoesNotUseLegacyUnknownFromAnotherPoint() throws Exception {
        UUID excursion=openPhysicalExcursion();Device legacy=requiredDevice("LEGACY-RECOVERY",null);
        provision(Set.of(first.sensor(),legacy.sensor()));
        ingest("/api/v1/measurements",reading(legacy,2,5),201);
        jdbc.update("UPDATE measurements SET source='LEGACY_UNKNOWN' WHERE sensor_node_id=?",legacy.sensor());
        ingest("/api/v1/measurements",reading(first,1,5),201);
        assertUnrecoveredPhysicalExcursion(excursion,"MISSING_POINT");
        ingest("/api/v1/measurements",reading(legacy,0,5),201);
        assertPhysicalRecovery(excursion,0);
    }
    @Test void physicalRecoveryUsesEligibleSensorSelectionAndSkipsInactiveSensors() throws Exception {
        UUID excursion=openPhysicalExcursion();Device alternate=requiredDevice("ALTERNATE-RECOVERY",first.point());
        Device inactive=requiredDevice("INACTIVE-RECOVERY",first.point());
        provision(Set.of(first.sensor(),alternate.sensor(),inactive.sensor()));
        ingest("/api/v1/measurements",reading(inactive,0,25),201);
        jdbc.update("UPDATE sensor_nodes SET status='INACTIVE' WHERE id=?",inactive.sensor());
        manual(first,50,25);
        ingest("/api/v1/measurements",reading(alternate,1,5),201);
        assertPhysicalRecovery(excursion,1);
    }

}
