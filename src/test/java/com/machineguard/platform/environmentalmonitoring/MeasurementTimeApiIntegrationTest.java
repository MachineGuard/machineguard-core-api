package com.machineguard.platform.environmentalmonitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machineguard.platform.iam.domain.model.*;
import com.machineguard.platform.iam.infrastructure.persistence.OrganizationEntity;
import com.machineguard.platform.iam.infrastructure.persistence.UserEntity;
import com.machineguard.platform.iam.infrastructure.persistence.repository.OrganizationJpaRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.machineguard.platform.iam.infrastructure.security.JwtTokenService;
import com.machineguard.platform.traceability.application.integration.InboundEvents;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:sampletime;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}")
@AutoConfigureMockMvc
@RecordApplicationEvents
class MeasurementTimeApiIntegrationTest {
    static final Instant NOW=Instant.parse("2026-11-04T12:00:00Z");
    static final UUID ORG=new UUID(0,2901), OTHER=new UUID(0,2902), ADMIN=new UUID(0,2903);
    @TestConfiguration static class Configuration {
        @Bean @Primary Clock sampleClock(){return Clock.fixed(NOW,ZoneOffset.UTC);}
        @Bean FailingLateConsumer failingLateConsumer(){return new FailingLateConsumer();}
    }
    static class FailingLateConsumer {
        boolean enabled;
        @EventListener public void on(Object event){
            if(event instanceof org.springframework.context.PayloadApplicationEvent<?> payload) event=payload.getPayload();
            if(enabled && event.getClass().getSimpleName().equals("LateDeviationDetected"))
                throw new IllegalStateException("Synthetic late consumer failure");
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired OrganizationJpaRepository organizations;
    @Autowired UserJpaRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ApplicationEvents events;
    @Autowired ApplicationEventPublisher bus;
    @Autowired FailingLateConsumer failing;
    UUID zone,point,sensor; String admin,credential;
    @BeforeEach void setup() throws Exception {
        if(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='late_deviations'",Integer.class)>0)
            jdbc.update("DELETE FROM late_deviations");
        for(String table:List.of("traceability_report_excursions","traceability_reports","non_conformities","excursions",
                "corrective_actions","alerts","incident_measurements","incidents","gateway_sensor_nodes","gateway_credentials",
                "measurements","thresholds","sensor_nodes","monitoring_points","monitoring_zones","initial_admin_invitations",
                "authentication_refresh_token_history","authentication_sessions","users","organizations"))jdbc.update("DELETE FROM "+table);
        organizations.save(new OrganizationEntity(ORG,"Synthetic",SubscriptionPlan.FREE,OrganizationStatus.ACTIVE,null,NOW));
        organizations.save(new OrganizationEntity(OTHER,"Other",SubscriptionPlan.FREE,OrganizationStatus.ACTIVE,null,NOW));
        users.save(new UserEntity(ADMIN,ORG,"admin@sample.test",passwords.encode("synthetic sample password"),"Admin",Role.ADMIN,UserStatus.ACTIVE,NOW));
        admin=auth(ORG,Role.ADMIN); zone=UUID.randomUUID();point=UUID.randomUUID();sensor=UUID.randomUUID();var at=OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC);
        jdbc.update("INSERT INTO monitoring_zones(id,organization_id,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)",zone,ORG,"Zone",at,at);
        jdbc.update("INSERT INTO monitoring_points(id,monitoring_zone_id,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)",point,zone,"Point",at,at);
        jdbc.update("INSERT INTO sensor_nodes(id,monitoring_point_id,device_code,sampling_interval_seconds,status,created_at) VALUES (?,?,?,10,'OFFLINE',?)",sensor,point,"TIME-NODE",at);
        for(String v:List.of("TEMPERATURE","HUMIDITY"))jdbc.update("INSERT INTO thresholds(id,monitoring_zone_id,environmental_variable,minimum_value,maximum_value,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),zone,v,v.equals("TEMPERATURE")?2:30,v.equals("TEMPERATURE")?8:75,at,at);
        credential=call(post("/api/v1/gateways"),Map.of("name","Time gateway","sensorNodeIds",List.of(sensor)),201,admin).get("credential").asText();
        failing.enabled=false;events.clear();
    }
    String auth(UUID org,Role role){return "Bearer "+tokens.issueAccessToken(ADMIN,org,role,UUID.randomUUID()).value();}
    JsonNode call(MockHttpServletRequestBuilder request,Object body,int expected,String bearer)throws Exception{
        if(bearer!=null)request.header("Authorization",bearer);
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var r=mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse();
        return r.getContentAsString().isEmpty()?json.nullNode():json.readTree(r.getContentAsString());
    }
    Map<String,Object> reading(int seconds,int value){return new HashMap<>(Map.of("deviceCode","TIME-NODE","temperature",value,"humidity",50,"capturedAt",NOW.plusSeconds(seconds).toString()));}
    JsonNode ingest(int seconds,int value,int expected)throws Exception{return call(post("/api/v1/measurements").header("X-Device-Credential",credential),reading(seconds,value),expected,null);}
    long count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class);}
    JsonNode alerts()throws Exception{return call(get("/api/v1/alerts").param("monitoringZoneId",zone.toString()),null,200,admin);}
    JsonNode detail(UUID id)throws Exception{return call(get("/api/v1/incidents/"+id),null,200,admin);}
    JsonNode late(String bearer)throws Exception{return call(get("/api/v1/traceability/late-deviations").param("monitoringZoneId",zone.toString()),null,200,bearer);}
    UUID incident()throws Exception{var rows=alerts();assertEquals(1,rows.size());return UUID.fromString(rows.get(0).get("incidentId").asText());}
    void resolve(UUID id)throws Exception{
        var alert=detail(id).get("alert").get("id").asText();
        call(post("/api/v1/alerts/"+alert+"/acknowledgements"),Map.of("note","Synthetic"),201,admin);
        call(post("/api/v1/incidents/"+id+"/corrective-actions"),Map.of("description","Synthetic action"),201,admin);
        call(post("/api/v1/incidents/"+id+"/resolve"),null,200,admin);
    }
    UUID orphanExcursion(int start,int end){
        var variable=com.machineguard.platform.traceability.domain.model.EnvironmentalVariable.TEMPERATURE;
        bus.publishEvent(new InboundEvents.DeviationDetected(ORG,zone,point,variable,12,8,NOW.plusSeconds(start)));
        UUID id=jdbc.queryForObject("SELECT id FROM excursions WHERE status='ONGOING'",UUID.class);
        bus.publishEvent(new InboundEvents.SafeRangeRestored(ORG,zone,variable,NOW.plusSeconds(end)));return id;
    }

    static java.util.stream.Stream<String> invalidTimes(){
        return java.util.stream.Stream.of(NOW.plusSeconds(61).toString(),NOW.plusSeconds(280).toString(),
                "1970-01-01T00:00:00Z",NOW.minus(Duration.ofDays(31)).toString(),"2026-11-04T12:00:00");
    }
    @ParameterizedTest @MethodSource("invalidTimes")
    void invalidGatewayTimesAreRejectedBeforeWriting(String at)throws Exception{
            var body=reading(0,5);body.put("capturedAt",at);
            call(post("/api/v1/measurements").header("X-Device-Credential",credential),body,400,null);
        assertEquals(0,count("measurements"));assertEquals(0,count("alerts"));
    }
    @ParameterizedTest @MethodSource("invalidTimes")
    void humanMeasurementCannotBypassTimePolicy(String at)throws Exception{
            call(post("/api/v1/environmental-monitoring/measurements"),Map.of("monitoringZoneId",zone,"monitoringPointId",point,"environmentalVariable","TEMPERATURE","measuredValue",5,"recordedAt",at),400,admin);
        assertEquals(0,count("measurements"));
    }
    @Test void timeBoundariesAndExplicitOffsetsAreAccepted()throws Exception{
        ingest(-30*86400,5,201);ingest(60,5,201);
        var body=reading(0,5);body.put("capturedAt",OffsetDateTime.ofInstant(NOW,ZoneOffset.ofHours(-5)).toString());
        call(post("/api/v1/measurements").header("X-Device-Credential",credential),body,201,null);
    }
    @Test void toleratedFutureReadingCannotSilenceFiveRealDeviations()throws Exception{
        ingest(50,5,201);for(int i=0;i<5;i++)ingest(i,25,201);
        UUID id=incident();assertTrue(detail(id).get("incident").get("historical").asBoolean());
        assertEquals(5,late(admin).size());
        assertEquals("NORMAL",call(get("/api/v1/environmental-monitoring/zones/"+zone),null,200,admin).get("environmentalCondition").asText());
        assertEquals(0,count("excursions"));
    }
    @Test void lateDeviationIsIdempotentAndDoesNotRewindLiveState()throws Exception{
        ingest(0,5,201);ingest(-60,25,201);UUID id=incident();
        assertTrue(detail(id).get("incident").get("historical").asBoolean());
        ingest(-60,25,200);assertEquals(1,late(admin).size());
        Object event=events.stream(Object.class).filter(e->e.getClass().getSimpleName().equals("LateDeviationDetected")).findFirst().orElse(null);
        assertNotNull(event);bus.publishEvent(event);bus.publishEvent(event);
        assertEquals(1,late(admin).size());assertEquals(1,count("incidents"));assertEquals(0,count("excursions"));
        assertEquals(NOW,jdbc.queryForObject("SELECT last_measurement_at FROM sensor_nodes WHERE id=?",OffsetDateTime.class,sensor).toInstant());
    }
    @Test void lateDeviationJoinsAnActiveLiveIncidentWithoutChangingItsPeak()throws Exception{
        ingest(0,11,201);UUID id=incident();ingest(-60,25,201);
        assertEquals(id,incident());assertTrue(detail(id).get("incident").has("historical"));
        assertFalse(detail(id).get("incident").get("historical").asBoolean());
        assertEquals(1,late(admin).size());assertEquals(11,jdbc.queryForObject("SELECT peak_value FROM excursions",Double.class));
    }
    @Test void historicalResolutionLeavesUnrelatedExcursionsAndSealedReportsUntouched()throws Exception{
        ingest(-100,5,201);ingest(-300,25,201);UUID id=incident();UUID orphan=orphanExcursion(-250,-200);
        var report=call(post("/api/v1/traceability/reports"),Map.of("monitoringZoneId",zone,"periodStart",NOW.minusSeconds(3600),"periodEnd",NOW),201,admin);
        UUID reportId=UUID.fromString(report.get("id").asText());String content=jdbc.queryForObject("SELECT content FROM traceability_reports WHERE id=?",String.class,reportId);
        ingest(-10,5,201); // Fresh physical recovery is now required even for a historical incident.
        resolve(id);
        assertNull(jdbc.queryForObject("SELECT incident_id FROM excursions WHERE id=?",UUID.class,orphan));
        assertEquals(content,jdbc.queryForObject("SELECT content FROM traceability_reports WHERE id=?",String.class,reportId));
        assertTrue(events.stream(Object.class).filter(e->e.getClass().getName().endsWith("AlertIncidentEvents$IncidentResolved"))
            .anyMatch(e->json.valueToTree(e).get("historical").asBoolean()));
    }
    @Test void currentEvidenceConvertsHistoricalIncidentAndKeepsTraceabilityPeriodNarrow()throws Exception{
        ingest(-100,5,201);ingest(-300,25,201);UUID id=incident();UUID orphan=orphanExcursion(-250,-200);
        ingest(-50,11,201);assertEquals(id,incident());assertFalse(detail(id).get("incident").get("historical").asBoolean());
        ingest(-10,5,201);resolve(id);
        assertNull(jdbc.queryForObject("SELECT incident_id FROM excursions WHERE id=?",UUID.class,orphan));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM excursions WHERE incident_id=?",Integer.class,id));
    }
    @Test void historyQueriesEnforceTenantAndViewerReadAccess()throws Exception{
        ingest(0,5,201);ingest(-60,25,201);
        assertEquals(1,late(auth(ORG,Role.VIEWER)).size());assertEquals(0,late(auth(OTHER,Role.ADMIN)).size());
        call(get("/api/v1/traceability/late-deviations").param("monitoringZoneId",zone.toString()),null,401,null);
        call(get("/api/v1/traceability/late-deviations").param("monitoringZoneId",zone.toString()).param("limit","501"),null,400,admin);
        assertEquals(0,call(get("/api/v1/traceability/late-deviations").param("monitoringZoneId",zone.toString())
                .header("X-Organization-Id",ORG),null,200,auth(OTHER,Role.VIEWER)).size());
    }
    @Test void lateConsumerFailureRollsBackMeasurementIncidentAndHistory()throws Exception{
        ingest(0,5,201);failing.enabled=true;
        assertThrows(jakarta.servlet.ServletException.class,()->ingest(-60,25,201));
        failing.enabled=false;assertEquals(2,count("measurements"));assertEquals(0,count("incidents"));assertEquals(0,late(admin).size());
    }
}
