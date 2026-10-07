package com.machineguard.platform.environmentalmonitoring;

import com.machineguard.platform.environmentalmonitoring.application.commandservices.RegisterMeasurementCommandService;
import com.machineguard.platform.environmentalmonitoring.domain.commands.RegisterMeasurementCommand;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.*;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.*;
import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import com.machineguard.platform.environmentalmonitoring.domain.repositories.*;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.infrastructure.security.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:environmentalmonitoring;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@RecordApplicationEvents
class EnvironmentalMonitoringApiIntegrationTest {
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-01T12:00:00Z");
    static final UUID ORG = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();
    static final String BASE = "/api/v1/environmental-monitoring";
    @TestConfiguration
    static class FixedClockConfig {
        @Bean FailingConsumer failingConsumer() { return new FailingConsumer(); }
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW.toInstant(), ZoneOffset.UTC); }
    }
    static class FailingConsumer {
        boolean enabled;
        @org.springframework.context.event.EventListener
        public void on(DeviationDetected event) {
            if (enabled) throw new IllegalStateException("Simulated downstream failure");
        }
    }
    @Autowired FailingConsumer failingConsumer;
    @Autowired MonitoringZoneRepository zones;
    @Autowired MonitoringPointRepository points;
    @Autowired SensorNodeRepository nodes;
    @Autowired ThresholdRepository thresholds;
    @Autowired MeasurementRepository measurements;
    @Autowired com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.repository.MeasurementJpaRepository measurementJpa;
    @Autowired RegisterMeasurementCommandService commands;
    @Autowired ApplicationEvents events;
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenService tokens;
    MonitoringZone zone;
    MonitoringPoint point;
    SensorNode node;
    @BeforeEach
    void setup() {
        for (String table : List.of("traceability_report_excursions", "traceability_reports", "non_conformities", "excursions",
                "measurements", "thresholds", "sensor_nodes", "monitoring_points", "monitoring_zones")) jdbc.update("DELETE FROM " + table);
        zone = zones.save(MonitoringZone.register(ORG, null, "Cold Room A", "Storage", NOW));
        point = points.save(MonitoringPoint.register(zone.getId(), "Point A", "Shelf B", NOW));
        node = nodes.save(SensorNode.register(point.getId(), "MG-01", 60, NOW));
        thresholds.save(Threshold.configure(zone.getId(), EnvironmentalVariable.TEMPERATURE, new BigDecimal("2"), new BigDecimal("8"), NOW));
        thresholds.save(Threshold.configure(zone.getId(), EnvironmentalVariable.HUMIDITY, new BigDecimal("35"), new BigDecimal("60"), NOW));
        failingConsumer.enabled = false;
        events.clear();
    }
    Measurement record(String value, int minute) {
        return record(point, node.getId(), EnvironmentalVariable.TEMPERATURE, value, minute);
    }
    Measurement record(MonitoringPoint target, UUID sensor, EnvironmentalVariable variable, String value, int minute) {
        return commands.handle(new RegisterMeasurementCommand(ORG, zone.getId(), target.getId(), sensor, variable,
                new BigDecimal(value), NOW.minusHours(1).plusMinutes(minute)));
    }
    String auth(UUID org, Role role) {
        return "Bearer " + tokens.issueAccessToken(UUID.randomUUID(), org, role, UUID.randomUUID()).value();
    }
    long deviations() { return events.stream(DeviationDetected.class).count(); }
    long restorations() { return events.stream(SafeRangeRestored.class).count(); }
    @Test void normalIsPersistedWithoutEventsAndPreservesBothTimes() {
        Measurement result = record("5", 0);
        Measurement saved = measurements.findByIdAndOrganizationId(result.getId(), ORG).orElseThrow();
        assertEquals(new BigDecimal("5.00"), saved.getMeasuredValue());
        assertEquals(NOW.minusHours(1).toInstant(), saved.getRecordedAt().toInstant());
        assertEquals(NOW.toInstant(), saved.getReceivedAt().toInstant());
        assertEquals(0, deviations()); assertEquals(0, restorations());
        assertEquals(SensorNodeStatus.ONLINE, nodes.findByIdAndMonitoringPointId(node.getId(), point.getId()).orElseThrow().getStatus());
    }
    @Test void normalToDeviationPublishesCompleteEventAndCreatesExcursion() {
        record("5", 0); Measurement reading = record("11.2", 1);
        var event = events.stream(DeviationDetected.class).findFirst().orElseThrow();
        assertEquals(reading.getId(), event.measurementId()); assertEquals(ORG, event.organizationId());
        assertEquals(zone.getId(), event.monitoringZoneId()); assertEquals(point.getId(), event.monitoringPointId());
        assertEquals(node.getId(), event.sensorNodeId()); assertEquals(new BigDecimal("8.00"), event.maximumValue());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM excursions", Integer.class));
    }
    @Test void successiveDeviationsUpdateOneExcursionPeak() {
        record("9", 0); record("11.2", 1); record("10.4", 2);
        assertEquals(3, deviations()); assertEquals(0, restorations());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM excursions", Integer.class));
        assertEquals(11.2, jdbc.queryForObject("SELECT peak_value FROM excursions", Double.class));
    }
    @Test void restorationOnlyOnTransitionClosesExcursion() {
        record("11.2", 0); record("10.4", 1); record("7.8", 2); record("7", 3);
        assertEquals(1, restorations());
        assertNotNull(jdbc.queryForObject("SELECT ended_at FROM excursions", Object.class));
    }
    @Test void inclusiveMinimumAndMaximumDoNotPublishDeviation() {
        record("2", 0); record("8", 1); assertEquals(0, deviations()); assertEquals(0, restorations());
    }
    @Test void independentVariablesDoNotRestoreEachOther() {
        record("11", 0); record(point, node.getId(), EnvironmentalVariable.HUMIDITY, "50", 0);
        assertEquals(1, deviations()); assertEquals(0, restorations());
        record(point, node.getId(), EnvironmentalVariable.HUMIDITY, "70", 1);
        record("5", 2); assertEquals(1, restorations());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM excursions WHERE ended_at IS NULL", Integer.class));
    }
    @Test void missingThresholdFailsWithoutPersisting() {
        jdbc.update("DELETE FROM thresholds");
        assertThrows(EnvironmentalMonitoringRuleException.class, () -> record("5", 0));
        assertEquals(0, measurementJpa.count()); assertEquals(0, deviations());
    }
    @Test void otherPointDeviationPreventsPrematureRestoration() {
        MonitoringPoint second = points.save(MonitoringPoint.register(zone.getId(), "B", null, NOW));
        record("11", 0); record(second, null, EnvironmentalVariable.TEMPERATURE, "10", 1);
        record("5", 2); assertEquals(0, restorations());
        record(second, null, EnvironmentalVariable.TEMPERATURE, "5", 3); assertEquals(1, restorations());
    }
    @Test void backfillCannotRewindLifecycleOrLatest() {
        record("11", 3); record("5", 1);
        assertEquals(2, measurementJpa.count()); assertEquals(0, restorations());
        assertEquals(new BigDecimal("11.00"), measurements.findLatest(ORG, zone.getId()).getFirst().getMeasuredValue());
    }
    @Test void repeatedTimestampIsRejectedIncludingHistoricalReading() {
        record("5", 1); record("6", 3);
        assertThrows(EnvironmentalMonitoringRuleException.class, () -> record("7", 1));
        assertEquals(2, measurementJpa.count());
    }
    @Test void tenantCannotRegisterInAnotherOrganization() {
        assertThrows(EnvironmentalMonitoringNotFoundException.class, () -> commands.handle(new RegisterMeasurementCommand(
                OTHER, zone.getId(), point.getId(), null, EnvironmentalVariable.TEMPERATURE, new BigDecimal("5"), NOW)));
        assertEquals(0, measurementJpa.count());
    }
    @Test void mismatchedPointAndSensorAreRejected() {
        var second = points.save(MonitoringPoint.register(zone.getId(), "B", null, NOW));
        assertThrows(EnvironmentalMonitoringNotFoundException.class, () -> record(second, node.getId(), EnvironmentalVariable.TEMPERATURE, "5", 0));
        var otherZone = zones.save(MonitoringZone.register(ORG, null, "Other", null, NOW));
        var otherPoint = points.save(MonitoringPoint.register(otherZone.getId(), "Other", null, NOW));
        assertThrows(EnvironmentalMonitoringNotFoundException.class, () -> record(otherPoint, null, EnvironmentalVariable.TEMPERATURE, "5", 0));
        assertEquals(0, measurementJpa.count());
    }
    @Test void httpRegistrationReturnsResourceAndLocation() throws Exception {
        Map<String, Object> body = Map.of("monitoringZoneId", zone.getId(), "monitoringPointId", point.getId(),
                "environmentalVariable", "TEMPERATURE", "measuredValue", 5, "recordedAt", NOW.minusHours(1).toString());
        mvc.perform(post(BASE + "/measurements").header("Authorization", auth(ORG, Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.organizationId").value(ORG.toString())).andExpect(jsonPath("$.measuredValue").value(5));
    }
    @Test void latestAndZonesExposeSeparateVariableReadingsAndNodeCounts() throws Exception {
        record("5", 0); record(point, node.getId(), EnvironmentalVariable.HUMIDITY, "50", 0);
        mvc.perform(get(BASE + "/measurements/latest").header("Authorization", auth(ORG, Role.VIEWER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(BASE + "/zones/" + zone.getId()).header("Authorization", auth(ORG, Role.VIEWER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.environmentalCondition").value("NORMAL"))
                .andExpect(jsonPath("$.sensorsOnline").value(1)).andExpect(jsonPath("$.thresholds.length()").value(2));
    }
    @Test void historyIsInclusiveFilteredAndPaginated() throws Exception {
        record("5", 0); record("6", 1); record(point, null, EnvironmentalVariable.HUMIDITY, "50", 0);
        mvc.perform(get(BASE + "/measurements/history").header("Authorization", auth(ORG, Role.VIEWER))
                .param("monitoringZoneId", zone.getId().toString()).param("environmentalVariable", "TEMPERATURE")
                .param("from", NOW.minusHours(1).toString()).param("to", NOW.minusHours(1).plusMinutes(1).toString()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items.length()").value(1));
    }
    @Test void tenantIsolationAppliesToAllReadsAndIgnoresForgedHeaders() throws Exception {
        var reading = record("5", 0);
        for (String path : List.of("/zones/" + zone.getId(), "/measurements/" + reading.getId(), "/measurements/latest?monitoringZoneId=" + zone.getId()))
            mvc.perform(get(BASE + path).header("Authorization", auth(OTHER, Role.ADMIN)).header("X-Organization-Id", ORG.toString())).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/measurements/latest").header("Authorization", auth(OTHER, Role.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(BASE + "/zones").header("Authorization", auth(OTHER, Role.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void authenticationAndAdminWritePermissionsAreEnforced() throws Exception {
        mvc.perform(get(BASE + "/zones")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/measurements").header("Authorization", auth(ORG, Role.VIEWER)).contentType(MediaType.APPLICATION_JSON)
                .content("{}" )).andExpect(status().isBadRequest());
        var body = Map.of("monitoringZoneId", zone.getId(), "monitoringPointId", point.getId(), "environmentalVariable", "TEMPERATURE", "measuredValue", 5, "recordedAt", NOW.toString());
        mvc.perform(post(BASE + "/measurements").header("Authorization", auth(ORG, Role.VIEWER)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isForbidden());
    }
    @Test void invalidHistoryAndBodyAreRejected() throws Exception {
        mvc.perform(get(BASE + "/measurements/history").header("Authorization", auth(ORG, Role.ADMIN))
                .param("monitoringZoneId", zone.getId().toString()).param("from", NOW.toString()).param("to", NOW.minusDays(1).toString()))
                .andExpect(status().isConflict());
        mvc.perform(post(BASE + "/measurements").header("Authorization", auth(ORG, Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"environmentalVariable\":\"INVALID\"}" )).andExpect(status().isBadRequest());
    }

    @Test void failedSynchronousConsumerRollsBackMeasurementSensorAndExcursion() {
        failingConsumer.enabled = true;
        assertThrows(IllegalStateException.class, () -> record("11", 0));
        assertEquals(0, measurementJpa.count());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM excursions", Integer.class));
        assertEquals(SensorNodeStatus.OFFLINE, nodes.findByIdAndMonitoringPointId(node.getId(), point.getId()).orElseThrow().getStatus());
    }
    @Test void lowerDeviationUsesMinimumInTraceabilityContract() {
        record("1", 0);
        assertEquals(2.0, jdbc.queryForObject("SELECT threshold_value FROM excursions", Double.class));
        record("2", 1); assertEquals(1, restorations());
    }
    @Test void restorationAcrossPointsUsesLatestZoneTime() {
        var second = points.save(MonitoringPoint.register(zone.getId(), "B", null, NOW));
        record("11", 10); record(second, null, EnvironmentalVariable.TEMPERATURE, "10", 1);
        record("5", 12); record(second, null, EnvironmentalVariable.TEMPERATURE, "5", 2);
        var event = events.stream(SafeRangeRestored.class).findFirst().orElseThrow();
        assertEquals(NOW.minusHours(1).plusMinutes(2), event.recordedAt());
        assertEquals(NOW.minusHours(1).plusMinutes(12), event.restoredAt());
        assertNotNull(jdbc.queryForObject("SELECT ended_at FROM excursions", Object.class));
    }
    @Test void inactiveZonePointOrNodeCannotReceiveMeasurements() {
        jdbc.update("UPDATE monitoring_zones SET status='INACTIVE' WHERE id=?", zone.getId());
        assertThrows(EnvironmentalMonitoringRuleException.class, () -> record("5", 0));
        jdbc.update("UPDATE monitoring_zones SET status='ACTIVE' WHERE id=?", zone.getId());
        jdbc.update("UPDATE monitoring_points SET status='INACTIVE' WHERE id=?", point.getId());
        assertThrows(EnvironmentalMonitoringRuleException.class, () -> record("5", 0));
        jdbc.update("UPDATE monitoring_points SET status='ACTIVE' WHERE id=?", point.getId());
        jdbc.update("UPDATE sensor_nodes SET status='INACTIVE' WHERE id=?", node.getId());
        assertThrows(EnvironmentalMonitoringRuleException.class, () -> record("5", 0));
        assertEquals(0, measurementJpa.count());
    }
    @Test void concurrentPointDeviationsKeepOneExcursion() throws Exception {
        var second = points.save(MonitoringPoint.register(zone.getId(), "B", null, NOW));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); return record("11", 0); });
            var other = executor.submit(() -> { start.await(); return record(second, null, EnvironmentalVariable.TEMPERATURE, "10", 1); });
            start.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS); other.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(2, measurementJpa.count());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM excursions", Integer.class));
        assertEquals(11.0, jdbc.queryForObject("SELECT peak_value FROM excursions", Double.class));
    }
    @Test void swaggerContainsEnvironmentalMonitoringContract() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/environmental-monitoring/measurements'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/environmental-monitoring/zones'].get").exists());
    }

    @Test void migratedMeasurementSchemaMatchesNumericNullableAndTimestampContract() {
        assertEquals(9, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='measurements'", Integer.class));
        assertEquals(6, jdbc.queryForObject("SELECT numeric_precision FROM information_schema.columns WHERE table_name='measurements' AND column_name='measured_value'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT numeric_scale FROM information_schema.columns WHERE table_name='measurements' AND column_name='measured_value'", Integer.class));
        assertEquals("YES", jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_name='measurements' AND column_name='sensor_node_id'", String.class));
        assertEquals("timestamp with time zone", jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_name='measurements' AND column_name='recorded_at'", String.class));
    }

    @Test void allEnvironmentalEntitiesValidateAgainstUnmodifiedV1() {
        var registry = new org.hibernate.boot.registry.StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.datasource", dataSource)
                .applySetting("hibernate.hbm2ddl.auto", "validate").build();
        try {
            var metadata = new org.hibernate.boot.MetadataSources(registry);
            for (Class<?> type : List.of(MonitoringZone.class, MonitoringPoint.class, SensorNode.class, Threshold.class, Measurement.class))
                metadata.addAnnotatedClass(type);
            try (var validated = metadata.buildMetadata().buildSessionFactory()) {
                assertTrue(validated.isOpen());
            }
        } finally {
            org.hibernate.boot.registry.StandardServiceRegistryBuilder.destroy(registry);
        }
    }
    @Test void existingTraceabilityHistoryReadsEnvironmentalMeasurements() throws Exception {
        record("11", 0); record("5", 1);
        mvc.perform(get("/api/v1/traceability/measurement-history").header("Authorization", auth(ORG, Role.VIEWER))
                .param("monitoringZoneId", zone.getId().toString()).param("from", NOW.minusHours(1).toString())
                .param("to", NOW.toString()).param("variable", "TEMPERATURE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
    }

    @Test void springWiresExactlyOneInfrastructureAdapterForEachDomainContract() {
        Map<Class<?>, Class<?>> expected = Map.of(
                MeasurementRepository.class, com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.adapter.JpaMeasurementRepositoryAdapter.class,
                ThresholdRepository.class, com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.adapter.JpaThresholdRepositoryAdapter.class,
                MonitoringZoneRepository.class, com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.adapter.JpaMonitoringZoneRepositoryAdapter.class,
                MonitoringPointRepository.class, com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.adapter.JpaMonitoringPointRepositoryAdapter.class,
                SensorNodeRepository.class, com.machineguard.platform.environmentalmonitoring.infrastructure.persistence.adapter.JpaSensorNodeRepositoryAdapter.class);
        expected.forEach((contract, adapter) -> {
            assertEquals(1, context.getBeansOfType(contract).size());
            assertEquals(adapter, org.springframework.aop.support.AopUtils.getTargetClass(context.getBean(contract)));
            assertFalse(org.springframework.data.repository.Repository.class.isAssignableFrom(contract));
        });
        for (Class<?> service : List.of(RegisterMeasurementCommandService.class,
                com.machineguard.platform.environmentalmonitoring.application.queryservices.EnvironmentalMonitoringQueryService.class)) {
            var parameters = Arrays.asList(service.getConstructors()[0].getParameterTypes());
            assertTrue(parameters.containsAll(expected.keySet()));
            assertTrue(parameters.stream().noneMatch(type -> type.getName().contains(".infrastructure.persistence.")));
        }
    }
    @Test void eventPublisherPortIsWiredWithoutConsumerOrTransportDependenciesInTheCommandService() {
        var port = com.machineguard.platform.environmentalmonitoring.application.ports.EnvironmentalMonitoringEventPublisher.class;
        assertEquals(1, context.getBeansOfType(port).size());
        assertEquals(com.machineguard.platform.environmentalmonitoring.infrastructure.integration.SpringEnvironmentalMonitoringEventPublisher.class,
                org.springframework.aop.support.AopUtils.getTargetClass(context.getBean(port)));
        var parameters = Arrays.asList(RegisterMeasurementCommandService.class.getConstructors()[0].getParameterTypes());
        assertTrue(parameters.contains(port));
        assertFalse(parameters.contains(org.springframework.context.ApplicationEventPublisher.class));
        assertTrue(parameters.stream().noneMatch(type -> type.getName().startsWith("com.machineguard.platform.traceability.")));
    }
    @Test void historyPortAppliesPointFilterAndPageOffsetInInfrastructure() {
        record("5", 0); record("6", 1);
        var second = points.save(MonitoringPoint.register(zone.getId(), "B", null, NOW));
        record(second, null, EnvironmentalVariable.TEMPERATURE, "7", 2);
        var page = measurements.findHistory(new com.machineguard.platform.environmentalmonitoring.domain.queries.EnvironmentalMonitoringQueries.GetMeasurementHistory(
                ORG, zone.getId(), point.getId(), EnvironmentalVariable.TEMPERATURE, NOW.minusHours(1), NOW, 1, 1));
        assertEquals(2, page.totalElements()); assertEquals(2, page.totalPages());
        assertEquals(1, page.page()); assertEquals(1, page.items().size());
        assertEquals(new BigDecimal("6.00"), page.items().getFirst().getMeasuredValue());
    }
}
