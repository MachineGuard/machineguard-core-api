package com.machineguard.platform.environmentalmonitoring;

import com.machineguard.platform.environmentalmonitoring.domain.exceptions.EnvironmentalMonitoringRuleException;
import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import com.machineguard.platform.environmentalmonitoring.domain.services.ZoneConditionService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentalMonitoringDomainTest {
    static final UUID ZONE = UUID.randomUUID();
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-01T12:00:00Z");
    Threshold threshold(String min, String max) {
        return Threshold.configure(ZONE, EnvironmentalVariable.TEMPERATURE, new BigDecimal(min), new BigDecimal(max), NOW);
    }
    Measurement reading(String value) {
        return Measurement.record(UUID.randomUUID(), ZONE, UUID.randomUUID(), null, EnvironmentalVariable.TEMPERATURE,
                new BigDecimal(value), NOW, NOW);
    }
    @Test void inclusiveRangeAndOutsideBoundaries() {
        Threshold threshold = threshold("2", "8");
        for (String value : List.of("2", "5", "8")) assertTrue(threshold.contains(new BigDecimal(value)));
        assertTrue(threshold.isOutOfRange(new BigDecimal("1.99")));
        assertTrue(threshold.isOutOfRange(new BigDecimal("8.01")));
        assertEquals(new BigDecimal("2.00"), threshold.exceededBoundary(new BigDecimal("1")));
        assertEquals(new BigDecimal("8.00"), threshold.exceededBoundary(new BigDecimal("9")));
    }
    @Test void invalidRangesAreRejected() {
        assertThrows(EnvironmentalMonitoringRuleException.class, () -> threshold("8", "2"));
        assertThrows(EnvironmentalMonitoringRuleException.class, () -> threshold("2", "2"));
    }
    @Test void numericPrecisionIsExactAndNeverSilentlyRounded() {
        assertEquals(new BigDecimal("9999.99"), reading("9999.99").getMeasuredValue());
        assertEquals(new BigDecimal("-9999.99"), reading("-9999.99").getMeasuredValue());
        assertThrows(EnvironmentalMonitoringRuleException.class, () -> reading("10000"));
        assertThrows(EnvironmentalMonitoringRuleException.class, () -> reading("5.001"));
    }
    @Test void nearLimitUsesTenPercentMargin() {
        var threshold = threshold("2", "8");
        assertTrue(threshold.isNearLimit(new BigDecimal("2.6")));
        assertTrue(threshold.isNearLimit(new BigDecimal("7.4")));
        assertFalse(threshold.isNearLimit(new BigDecimal("5")));
        assertFalse(threshold.isNearLimit(new BigDecimal("9")));
    }
    @Test void sensorIntervalMustBePositive() {
        assertThrows(EnvironmentalMonitoringRuleException.class, () -> SensorNode.register(UUID.randomUUID(), "node", 0, NOW));
    }
    @Test void sensorTimestampDoesNotRewind() {
        var node = SensorNode.register(UUID.randomUUID(), "node", 60, NOW);
        node.recordMeasurement(NOW); node.recordMeasurement(NOW.minusHours(1));
        assertEquals(NOW, node.getLastMeasurementAt()); assertEquals(SensorNodeStatus.ONLINE, node.getStatus());
    }
    @Test void zoneConditionDistinguishesMissingDataAndOffline() {
        assertEquals("NO_DATA", ZoneConditionService.evaluate(List.of(), List.of(), List.of()));
        var node = SensorNode.register(UUID.randomUUID(), "node", 60, NOW);
        assertEquals("OFFLINE", ZoneConditionService.evaluate(List.of(node), List.of(threshold("2", "8")), List.of(reading("5"))));
    }
    @Test void zoneConditionUsesRangeThenWarningMargin() {
        var thresholds = List.of(threshold("2", "8"));
        assertEquals("NORMAL", ZoneConditionService.evaluate(List.of(), thresholds, List.of(reading("5"))));
        assertEquals("NEAR_LIMIT", ZoneConditionService.evaluate(List.of(), thresholds, List.of(reading("8"))));
        assertEquals("OUT_OF_RANGE", ZoneConditionService.evaluate(List.of(), thresholds, List.of(reading("9"))));
    }
}
