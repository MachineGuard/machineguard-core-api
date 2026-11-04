package com.machineguard.platform.environmentalmonitoring;

import com.machineguard.platform.environmentalmonitoring.application.MeasurementTimePolicy;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class MeasurementTimePolicyTest {
    static final Instant NOW=Instant.parse("2026-11-04T12:00:00Z");
    static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
    static OffsetDateTime at(Instant value){return value.atOffset(ZoneOffset.UTC);}
    @Test void configuredFutureAndAgeLimitsAreInclusive(){
        var policy=new MeasurementTimePolicy(CLOCK,5,1,"2026-01-01T00:00:00Z");
        assertDoesNotThrow(()->policy.validate(at(NOW.plusSeconds(5))));
        assertDoesNotThrow(()->policy.validate(at(NOW.minusSeconds(86400))));
        assertThrows(IllegalArgumentException.class,()->policy.validate(at(NOW.plusSeconds(6))));
        assertThrows(IllegalArgumentException.class,()->policy.validate(at(NOW.minusSeconds(86401))));
    }
    @Test void minimumIsIndependentOfMaximumAge(){
        var policy=new MeasurementTimePolicy(CLOCK,0,30,"2026-11-04T11:59:59Z");
        assertDoesNotThrow(()->policy.validate(at(NOW.minusSeconds(1))));
        assertThrows(IllegalArgumentException.class,()->policy.validate(at(NOW.minusSeconds(2))));
    }
    @Test void invalidOrOverflowingConfigurationFailsBeforeRequests(){
        assertThrows(RuntimeException.class,()->new MeasurementTimePolicy(CLOCK,-1,30,NOW.toString()));
        assertThrows(RuntimeException.class,()->new MeasurementTimePolicy(CLOCK,60,0,NOW.toString()));
        assertThrows(RuntimeException.class,()->new MeasurementTimePolicy(CLOCK,60,30,"2026-01-01"));
        assertThrows(RuntimeException.class,()->new MeasurementTimePolicy(CLOCK,60,Long.MAX_VALUE,NOW.toString()));
        assertThrows(RuntimeException.class,()->new MeasurementTimePolicy(CLOCK,Long.MAX_VALUE,30,NOW.toString()));
    }
}
