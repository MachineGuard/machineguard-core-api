package com.machineguard.platform.traceability.domain;

import com.machineguard.platform.traceability.domain.exceptions.DomainRuleViolationException;
import com.machineguard.platform.traceability.domain.model.Batch;
import com.machineguard.platform.traceability.domain.model.Disposition;
import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;
import com.machineguard.platform.traceability.domain.model.Excursion;
import com.machineguard.platform.traceability.domain.model.ExcursionPeriod;
import com.machineguard.platform.traceability.domain.model.ExcursionSeverity;
import com.machineguard.platform.traceability.domain.model.NonConformity;
import com.machineguard.platform.traceability.domain.model.ReportPeriod;
import com.machineguard.platform.traceability.domain.model.RetentionPeriod;
import com.machineguard.platform.traceability.domain.model.TraceabilityReport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainModelTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    private Excursion highTemperatureExcursion() {
        return Excursion.start(1L, 10L, 100L, EnvironmentalVariable.TEMPERATURE, 8.5, 8.0, T0);
    }

    @Test
    void excursionKeepsMostExtremeValueAsPeak() {
        Excursion e = highTemperatureExcursion();
        e.registerDeviation(11.0, T0.plusSeconds(60));
        e.registerDeviation(9.0, T0.plusSeconds(120));
        assertEquals(11.0, e.getPeakValue());
        assertTrue(e.isOngoing());
    }

    @Test
    void peakForLowBreachIsTheLowestValue() {
        Excursion e = Excursion.start(1L, 10L, 100L, EnvironmentalVariable.TEMPERATURE, 1.5, 2.0, T0);
        e.registerDeviation(-1.0, T0.plusSeconds(30));
        e.registerDeviation(0.5, T0.plusSeconds(60));
        assertEquals(-1.0, e.getPeakValue());
    }

    @Test
    void endComputesDurationAndSeverityNeverDecreases() {
        Excursion e = highTemperatureExcursion();
        e.registerDeviation(14.0, T0.plusSeconds(60)); // magnitud 6 -> HIGH
        assertEquals(ExcursionSeverity.HIGH, e.getSeverity());
        e.end(T0.plus(Duration.ofMinutes(90)));
        assertEquals(Duration.ofMinutes(90), e.calculateDuration(T0.plus(Duration.ofDays(1))));
        assertEquals(ExcursionSeverity.CRITICAL, e.getSeverity()); // 90 min escala un nivel
        assertFalse(e.isOngoing());
    }

    @Test
    void closedExcursionRejectsFurtherChanges() {
        Excursion e = highTemperatureExcursion();
        e.end(T0.plusSeconds(60));
        assertThrows(DomainRuleViolationException.class, () -> e.registerDeviation(12.0, T0.plusSeconds(90)));
        assertThrows(DomainRuleViolationException.class, () -> e.end(T0.plusSeconds(120)));
    }

    @Test
    void endBeforeStartIsRejected() {
        Excursion e = highTemperatureExcursion();
        assertThrows(IllegalArgumentException.class, () -> e.end(T0.minusSeconds(1)));
    }

    @Test
    void firstLinkedIncidentPrevails() {
        Excursion e = highTemperatureExcursion();
        assertTrue(e.linkIncident(7L, "Se reparo la camara"));
        assertFalse(e.linkIncident(8L, "otra"));
        assertEquals(7L, e.getIncidentId());
        assertEquals("Se reparo la camara", e.getCorrectiveAction());
    }

    @Test
    void severityClassificationByMagnitudeAndDuration() {
        assertEquals(ExcursionSeverity.LOW, ExcursionSeverity.classify(1.0, Duration.ZERO));
        assertEquals(ExcursionSeverity.MEDIUM, ExcursionSeverity.classify(1.0, Duration.ofHours(2)));
        assertEquals(ExcursionSeverity.MEDIUM, ExcursionSeverity.classify(3.0, Duration.ofMinutes(10)));
        assertEquals(ExcursionSeverity.HIGH, ExcursionSeverity.classify(7.0, Duration.ZERO));
        assertEquals(ExcursionSeverity.CRITICAL, ExcursionSeverity.classify(12.0, Duration.ZERO));
        assertEquals(ExcursionSeverity.CRITICAL, ExcursionSeverity.classify(12.0, Duration.ofHours(5)));
    }

    @Test
    void excursionPeriodOverlapHandlesOpenEnds() {
        ExcursionPeriod open = new ExcursionPeriod(T0, null);
        ExcursionPeriod closed = new ExcursionPeriod(T0.minusSeconds(600), T0.minusSeconds(60));
        ExcursionPeriod later = new ExcursionPeriod(T0.plusSeconds(60), T0.plusSeconds(120));
        assertFalse(open.overlaps(closed));
        assertTrue(open.overlaps(later));
        assertTrue(open.contains(T0.plusSeconds(10_000)));
        assertThrows(IllegalArgumentException.class, () -> new ExcursionPeriod(T0, T0.minusSeconds(1)));
    }

    @Test
    void nonConformityStartsQuarantinedAndReleaseRequiresJustification() {
        NonConformity nc = NonConformity.register(1L, 1L, new Batch("L-001", "Vacuna", new BigDecimal("50"), T0),
                "Cadena de frio", 5L, T0);
        assertEquals(Disposition.QUARANTINED, nc.getDisposition());
        assertThrows(DomainRuleViolationException.class, () -> nc.release("  ", T0));
        assertFalse(nc.isResolved());

        nc.release("Analisis de laboratorio conforme", T0.plusSeconds(3600));
        assertEquals(Disposition.RELEASED, nc.getDisposition());
        assertTrue(nc.isResolved());
        assertThrows(DomainRuleViolationException.class, () -> nc.discard("x", T0));
    }

    @Test
    void batchRequiresCodeAndPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> new Batch(" ", "x", BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> new Batch("L1", "x", BigDecimal.ZERO, null));
    }

    @Test
    void reportCannotIncludeOngoingExcursionsAndIsImmutableOnceSealed() {
        ReportPeriod period = new ReportPeriod(T0.minus(Duration.ofDays(1)), T0);
        TraceabilityReport report = TraceabilityReport.generate(1L, 10L, period, 5L, T0);
        assertThrows(DomainRuleViolationException.class, () -> report.includeExcursion(highTemperatureExcursion()));

        report.includeMeasurementHistory(42);
        report.seal("{\"excursions\":[]}");
        assertTrue(report.isSealed());
        assertTrue(report.verifyIntegrity());
        assertEquals(64, report.getChecksum().length());
        assertThrows(DomainRuleViolationException.class, () -> report.includeMeasurementHistory(1));
        assertThrows(DomainRuleViolationException.class, () -> report.seal("{}"));
    }

    @Test
    void reportPeriodValidation() {
        Instant now = T0;
        assertTrue(new ReportPeriod(now.minus(Duration.ofDays(30)), now).isValid(now, 366));
        assertFalse(new ReportPeriod(now.minusSeconds(10), now.minusSeconds(20)).isValid(now, 366));
        assertFalse(new ReportPeriod(now.minusSeconds(10), now.plusSeconds(20)).isValid(now, 366));
        assertFalse(new ReportPeriod(now.minus(Duration.ofDays(400)), now).isValid(now, 366));
        assertThrows(IllegalArgumentException.class, () -> new ReportPeriod(null, now));
    }

    @Test
    void retentionPeriodExpiry() {
        RetentionPeriod retention = new RetentionPeriod(12);
        Instant recorded = Instant.parse("2025-01-15T00:00:00Z");
        assertEquals(Instant.parse("2026-01-15T00:00:00Z"), retention.expirationDateFrom(recorded));
        assertTrue(retention.isExpired(recorded, Instant.parse("2026-01-16T00:00:00Z")));
        assertFalse(retention.isExpired(recorded, Instant.parse("2025-12-31T00:00:00Z")));
        assertThrows(IllegalArgumentException.class, () -> new RetentionPeriod(0));
        assertNull(highTemperatureExcursion().getEndedAt());
    }
}
