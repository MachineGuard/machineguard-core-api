package com.machineguard.platform.traceability;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.machineguard.platform.traceability.application.integration.InboundEvents.DeviationDetected;
import com.machineguard.platform.traceability.application.integration.InboundEvents.IncidentResolved;
import com.machineguard.platform.traceability.application.integration.InboundEvents.SafeRangeRestored;
import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.infrastructure.security.JwtTokenService;
import com.machineguard.platform.traceability.domain.model.ReportPeriod;
import com.machineguard.platform.traceability.domain.model.RetentionPeriod;
import com.machineguard.platform.traceability.domain.model.TraceabilityReport;
import com.machineguard.platform.traceability.domain.repositories.TraceabilityReportRepository;
import com.machineguard.platform.traceability.domain.services.TraceabilityReportAssemblyService;
import com.machineguard.platform.traceability.domain.services.RetentionPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TraceabilityApiIntegrationTest {

    static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    static final UUID ORG = uuid(1);
    static final UUID OTHER_ORG = uuid(2);
    static final UUID USER = uuid(5);
    static final UUID ZONE = uuid(10);
    static final UUID POINT = uuid(100);
    static UUID uuid(long n) {
        return new UUID(0, n);
    }

    static final String BASE = "/api/v1/traceability";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEventPublisher events;
    @Autowired RetentionPolicyService retention;
    @Autowired JwtTokenService jwtTokens;
    @Autowired TraceabilityReportAssemblyService reportAssembly;
    @Autowired TraceabilityReportRepository reports;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("DROP TABLE IF EXISTS measurements");
        jdbc.execute("""
                CREATE TABLE measurements (organization_id UUID, monitoring_zone_id UUID,
                  monitoring_point_id UUID, environmental_variable VARCHAR(20), measured_value DOUBLE PRECISION,
                  recorded_at TIMESTAMP WITH TIME ZONE)""");
        jdbc.execute("DELETE FROM traceability_report_excursions");
        jdbc.execute("DELETE FROM traceability_reports");
        jdbc.execute("DELETE FROM non_conformities");
        jdbc.execute("DELETE FROM excursions");
    }

    @Test
    void newSealedSnapshotsKeepBoot3ContentBytesAndChecksum() {
        Instant generatedAt = Instant.parse("2026-01-01T01:00:00Z");
        var report = TraceabilityReport.generate(uuid(10), uuid(20),
                new ReportPeriod(Instant.parse("2026-01-01T00:00:00Z"), generatedAt), uuid(30), generatedAt);
        var measurement = new com.machineguard.platform.traceability.application.ports.MeasurementHistoryProvider.MeasurementPoint(
                uuid(40), EnvironmentalVariable.HUMIDITY, 47.25, generatedAt);

        reportAssembly.assembleAndSeal(report, List.of(), List.of(), List.of(measurement), 1);

        assertEquals("{\"excursions\":[],\"correctiveActions\":[],\"ongoingExcursionsExcluded\":1,\"measurementHistory\":[{\"monitoringPointId\":\"00000000-0000-0000-0000-000000000028\",\"environmentalVariable\":\"HUMIDITY\",\"value\":47.25,\"recordedAt\":\"2026-01-01T01:00:00Z\"}]}",
                report.getContent());
        assertEquals("0fc1a59783dd7c7514da5902a76d9f436b4e58eaccdb07a7b7156efe1720db88", report.getChecksum());
        assertTrue(report.verifyIntegrity());

        TraceabilityReport persisted = reports.saveAndFlush(report);
        TraceabilityReport loaded = reports.findById(persisted.getId()).orElseThrow();
        assertEquals(report.getContent(), loaded.getContent());
        assertEquals(report.getChecksum(), loaded.getChecksum());
        assertTrue(loaded.verifyIntegrity());
    }

    private MockHttpServletRequestBuilder withIdentity(MockHttpServletRequestBuilder request, UUID org) {
        String accessToken = com.machineguard.platform.support.SessionFixtures.token(jdbc,jwtTokens,USER, org, Role.ADMIN, uuid(888));
        UUID spoofedOrganization = org.equals(ORG) ? OTHER_ORG : ORG;
        return request.header("Authorization", "Bearer " + accessToken)
                .header("X-Organization-Id", spoofedOrganization).header("X-User-Id", uuid(777));
    }

    private MockHttpServletRequestBuilder withIdentity(MockHttpServletRequestBuilder request) {
        return withIdentity(request, ORG);
    }

    private void deviation(double value, Instant at) {
        events.publishEvent(new DeviationDetected(ORG, ZONE, POINT, EnvironmentalVariable.TEMPERATURE, value, 8.0, at));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode openExcursions() throws Exception {
        return body(mvc.perform(withIdentity(get(BASE + "/excursions/open"))).andExpect(status().isOk()).andReturn());
    }

    private UUID createClosedExcursion(Instant start, Instant end, UUID incidentId) throws Exception {
        deviation(9.5, start);
        events.publishEvent(new SafeRangeRestored(ORG, ZONE, EnvironmentalVariable.TEMPERATURE, end));
        if (incidentId != null) {
            events.publishEvent(new IncidentResolved(ORG, incidentId, ZONE, EnvironmentalVariable.TEMPERATURE,
                    "Se reemplazo el compresor", end.plusSeconds(60)));
        }
        JsonNode all = body(mvc.perform(withIdentity(get(BASE + "/excursions?status=CLOSED"))).andReturn());
        return UUID.fromString(all.get(0).get("id").asText());
    }

    private void measurement(Instant at, double value) {
        jdbc.update("INSERT INTO measurements VALUES (?,?,?,?,?,?)", ORG, ZONE, POINT, "TEMPERATURE", value,
                Timestamp.from(at));
    }

    @Test
    void consecutiveDeviationsKeepASingleOpenExcursionWithUpdatedPeak() throws Exception {
        deviation(8.5, NOW.minusSeconds(600));
        deviation(11.0, NOW.minusSeconds(300));
        deviation(9.0, NOW.minusSeconds(100));

        JsonNode open = openExcursions();
        assertEquals(1, open.size());
        assertEquals(11.0, open.get(0).get("peakValue").asDouble());
        assertEquals("ONGOING", open.get(0).get("status").asText());
        assertEquals("MEDIUM", open.get(0).get("severity").asText());
    }

    @Test
    void incidentResolvedLinksCorrectiveActionAndClosesExcursionIdempotently() throws Exception {
        deviation(9.5, NOW.minusSeconds(1800));
        IncidentResolved resolved = new IncidentResolved(ORG, uuid(77), ZONE, EnvironmentalVariable.TEMPERATURE,
                "Se reemplazo el compresor", NOW.minusSeconds(600));
        events.publishEvent(resolved);
        events.publishEvent(resolved);

        assertEquals(0, openExcursions().size());
        JsonNode closed = body(mvc.perform(withIdentity(get(BASE + "/excursions?status=CLOSED"))).andReturn());
        assertEquals(1, closed.size());
        assertEquals(uuid(77).toString(), closed.get(0).get("incidentId").asText());
        assertEquals("Se reemplazo el compresor", closed.get(0).get("correctiveAction").asText());
        assertEquals(1200, closed.get(0).get("durationSeconds").asLong());
    }

    @Test
    void incidentResolvedAfterSafeRangeRestoredLinksTheClosedExcursion() throws Exception {
        UUID id = createClosedExcursion(NOW.minusSeconds(3600), NOW.minusSeconds(3000), null);
        events.publishEvent(new IncidentResolved(ORG, uuid(88), ZONE, null, "Se ajusto el termostato", NOW.minusSeconds(100)));

        mvc.perform(withIdentity(get(BASE + "/excursions/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excursion.incidentId").value(uuid(88).toString()));
    }

    @Test
    void historyFiltersByZoneAndPeriodAndIsolatesOrganizations() throws Exception {
        createClosedExcursion(NOW.minusSeconds(7200), NOW.minusSeconds(6000), null);

        mvc.perform(withIdentity(get(BASE + "/excursions?monitoringZoneId=" + ZONE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(withIdentity(get(BASE + "/excursions?monitoringZoneId=" + uuid(99))))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(withIdentity(get(BASE + "/excursions?from=" + NOW.minusSeconds(60))))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(withIdentity(get(BASE + "/excursions"), OTHER_ORG))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(BASE + "/excursions")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonConformityLifecycleEnforcesBusinessRules() throws Exception {
        UUID excursionId = createClosedExcursion(NOW.minusSeconds(7200), NOW.minusSeconds(6000), null);
        String create = """
                {"batchCode":"L-2026-001","productName":"Insulina","quantity":120,"classification":"Cadena de frio"}""";

        MvcResult created = mvc.perform(withIdentity(post(BASE + "/excursions/" + excursionId + "/non-conformities"))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disposition").value("QUARANTINED"))
                .andExpect(jsonPath("$.registeredBy").value(USER.toString()))
                .andReturn();
        String ncId = body(created).get("id").asText();

        String release = "{\"disposition\":\"RELEASED\"}";
        mvc.perform(withIdentity(patch(BASE + "/non-conformities/" + ncId + "/disposition"))
                        .contentType(MediaType.APPLICATION_JSON).content(release))
                .andExpect(status().isConflict());

        mvc.perform(withIdentity(patch(BASE + "/non-conformities/" + ncId + "/disposition"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"RELEASED\",\"justification\":\"Laboratorio confirmo estabilidad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disposition").value("RELEASED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        mvc.perform(withIdentity(patch(BASE + "/non-conformities/" + ncId + "/disposition"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"disposition\":\"DISCARDED\"}"))
                .andExpect(status().isConflict());

        mvc.perform(withIdentity(get(BASE + "/excursions/" + excursionId)))
                .andExpect(jsonPath("$.nonConformities.length()").value(1));
    }

    @Test
    void nonConformityRequiresExistingExcursionOfTheSameOrganizationAndValidBody() throws Exception {
        UUID excursionId = createClosedExcursion(NOW.minusSeconds(7200), NOW.minusSeconds(6000), null);
        String create = "{\"batchCode\":\"L1\",\"classification\":\"x\"}";

        mvc.perform(withIdentity(post(BASE + "/excursions/" + uuid(9999) + "/non-conformities"))
                .contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isNotFound());
        mvc.perform(withIdentity(post(BASE + "/excursions/" + excursionId + "/non-conformities"), OTHER_ORG)
                .contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isNotFound());
        mvc.perform(withIdentity(post(BASE + "/excursions/" + excursionId + "/non-conformities"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"classification\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generatesSealedReportWithEvidenceAndVerifiableIntegrity() throws Exception {
        UUID closedId = createClosedExcursion(NOW.minusSeconds(7200), NOW.minusSeconds(6000), uuid(55));
        deviation(12.0, NOW.minusSeconds(300)); // sigue abierta: no entra como evidencia cerrada
        measurement(NOW.minusSeconds(7000), 9.5);
        measurement(NOW.minusSeconds(6500), 10.1);
        measurement(NOW.minus(java.time.Duration.ofDays(5)), 5.0); // fuera del periodo
        mvc.perform(withIdentity(post(BASE + "/excursions/" + closedId + "/non-conformities"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchCode\":\"L-9\",\"classification\":\"Cadena de frio\"}")).andExpect(status().isCreated());

        String request = "{\"monitoringZoneId\":\"" + ZONE + "\",\"periodStart\":\"" + NOW.minus(java.time.Duration.ofDays(1))
                + "\",\"periodEnd\":\"" + NOW + "\"}";
        MvcResult result = mvc.perform(withIdentity(post(BASE + "/reports"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SEALED"))
                .andExpect(jsonPath("$.excursionCount").value(1))
                .andExpect(jsonPath("$.measurementCount").value(2))
                .andExpect(jsonPath("$.integrityVerified").value(true))
                .andExpect(jsonPath("$.content.ongoingExcursionsExcluded").value(1))
                .andExpect(jsonPath("$.content.correctiveActions[0].incidentId").value(uuid(55).toString()))
                .andExpect(jsonPath("$.content.excursions[0].nonConformities[0].batchCode").value("L-9"))
                .andReturn();
        JsonNode report = body(result);
        assertEquals(64, report.get("checksum").asText().length());
        assertEquals(closedId.toString(), report.get("excursionIds").get(0).asText());
        assertEquals(2, report.get("content").get("measurementHistory").size());

        String reportId = report.get("id").asText();
        MvcResult fetched = mvc.perform(withIdentity(get(BASE + "/reports/" + reportId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.integrityVerified").value(true)).andReturn();
        assertEquals(report.get("checksum").asText(), body(fetched).get("checksum").asText());

        mvc.perform(withIdentity(get(BASE + "/reports")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").doesNotExist());
        mvc.perform(withIdentity(get(BASE + "/reports/" + reportId + "/download")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        mvc.perform(withIdentity(get(BASE + "/reports/" + reportId), OTHER_ORG)).andExpect(status().isNotFound());

        // un reporte sellado no cambia aunque la no conformidad se modifique despues
        jdbc.update("UPDATE excursions SET peak_value = 99 WHERE id = ?", closedId);
        mvc.perform(withIdentity(get(BASE + "/reports/" + reportId))).andExpect(jsonPath("$.integrityVerified").value(true));
    }

    @Test
    void tamperedReportFailsIntegrityVerification() throws Exception {
        createClosedExcursion(NOW.minusSeconds(7200), NOW.minusSeconds(6000), null);
        String request = "{\"monitoringZoneId\":\"" + ZONE + "\",\"periodStart\":\"" + NOW.minus(java.time.Duration.ofDays(1))
                + "\",\"periodEnd\":\"" + NOW + "\"}";
        String reportId = body(mvc.perform(withIdentity(post(BASE + "/reports"))
                .contentType(MediaType.APPLICATION_JSON).content(request)).andReturn()).get("id").asText();

        jdbc.update("UPDATE traceability_reports SET measurement_count = 999 WHERE id = CAST(? AS UUID)", reportId);
        mvc.perform(withIdentity(get(BASE + "/reports/" + reportId))).andExpect(jsonPath("$.integrityVerified").value(false));
    }

    @Test
    void reportRequestValidation() throws Exception {
        String future = "{\"monitoringZoneId\":\"" + ZONE + "\",\"periodStart\":\"" + NOW + "\",\"periodEnd\":\""
                + NOW.plusSeconds(3600) + "\"}";
        String inverted = "{\"monitoringZoneId\":\"" + ZONE + "\",\"periodStart\":\"" + NOW + "\",\"periodEnd\":\""
                + NOW.minusSeconds(3600) + "\"}";
        String tooLong = "{\"monitoringZoneId\":\"" + ZONE + "\",\"periodStart\":\"" + NOW.minus(java.time.Duration.ofDays(500))
                + "\",\"periodEnd\":\"" + NOW + "\"}";
        for (String bad : new String[]{future, inverted, tooLong, "{\"monitoringZoneId\":\"" + ZONE + "\"}"}) {
            mvc.perform(withIdentity(post(BASE + "/reports")).contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void reportReturns503WhenMeasurementHistoryIsUnavailable() throws Exception {
        jdbc.execute("DROP TABLE measurements");
        String request = "{\"monitoringZoneId\":\"" + ZONE + "\",\"periodStart\":\"" + NOW.minus(java.time.Duration.ofDays(1))
                + "\",\"periodEnd\":\"" + NOW + "\"}";
        mvc.perform(withIdentity(post(BASE + "/reports")).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void measurementHistoryByZoneAndByExcursion() throws Exception {
        UUID excursionId = createClosedExcursion(NOW.minusSeconds(7200), NOW.minusSeconds(6000), null);
        measurement(NOW.minusSeconds(7100), 9.0);
        measurement(NOW.minusSeconds(6500), 10.0);
        measurement(NOW.minusSeconds(3000), 5.0);

        mvc.perform(withIdentity(get(BASE + "/measurement-history?monitoringZoneId=" + ZONE + "&from="
                        + NOW.minusSeconds(8000) + "&to=" + NOW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(3));
        mvc.perform(withIdentity(get(BASE + "/excursions/" + excursionId + "/measurement-history")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
        mvc.perform(withIdentity(get(BASE + "/measurement-history?monitoringZoneId=" + ZONE + "&from="
                        + NOW + "&to=" + NOW.minusSeconds(10))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retentionPurgesExpiredExcursionsExceptThoseInSealedReports() throws Exception {
        Instant old = NOW.minus(java.time.Duration.ofDays(500));
        UUID inReport = createClosedExcursion(old, old.plusSeconds(600), null);
        deviation(9.0, old.plusSeconds(86_400));
        events.publishEvent(new SafeRangeRestored(ORG, ZONE, EnvironmentalVariable.TEMPERATURE, old.plusSeconds(87_000)));
        // un reporte sellado que cubre solo la primera excursion
        String request = "{\"monitoringZoneId\":\"" + ZONE + "\",\"periodStart\":\"" + old.minusSeconds(60)
                + "\",\"periodEnd\":\"" + old.plusSeconds(3600) + "\"}";
        mvc.perform(withIdentity(post(BASE + "/reports")).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.excursionIds[0]").value(inReport.toString()));
        // una excursion reciente nunca se purga
        deviation(9.0, NOW.minusSeconds(100));

        assertEquals(1, retention.purgeExpired(new RetentionPeriod(12)));

        JsonNode remaining = body(mvc.perform(withIdentity(get(BASE + "/excursions"))).andReturn());
        assertEquals(2, remaining.size());
        assertTrue(remaining.findValues("id").stream().anyMatch(n -> n.asText().equals(inReport.toString())));
        assertFalse(remaining.findValues("status").stream().allMatch(n -> n.asText().equals("CLOSED")));
        assertNotNull(remaining.get(0));
    }

    @Test
    void openApiDocumentationIsServed() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/traceability/reports']").exists());
    }
}
