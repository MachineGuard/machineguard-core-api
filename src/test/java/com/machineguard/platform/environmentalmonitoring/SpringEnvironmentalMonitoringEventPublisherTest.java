package com.machineguard.platform.environmentalmonitoring;

import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.DeviationDetected;
import com.machineguard.platform.environmentalmonitoring.domain.events.EnvironmentalMonitoringEvents.SafeRangeRestored;
import com.machineguard.platform.environmentalmonitoring.domain.model.EnvironmentalVariable;
import com.machineguard.platform.environmentalmonitoring.infrastructure.integration.SpringEnvironmentalMonitoringEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;

class SpringEnvironmentalMonitoringEventPublisherTest {
    private final ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
    private final SpringEnvironmentalMonitoringEventPublisher publisher = new SpringEnvironmentalMonitoringEventPublisher(spring);

    @Test
    void publishesCompleteProducerDeviationWithoutReplacingItWithAConsumerContract() {
        var event = new DeviationDetected(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, EnvironmentalVariable.TEMPERATURE, new BigDecimal("11.20"),
                new BigDecimal("2.00"), new BigDecimal("8.00"), OffsetDateTime.parse("2026-10-01T11:00:00Z"));

        publisher.publish(event);

        verify(spring).publishEvent(same(event));
        verifyNoMoreInteractions(spring);
    }

    @Test
    void publishesRestorationWithOriginalRecordedTimeAndZoneRestorationTime() {
        var recordedAt = OffsetDateTime.parse("2026-10-01T11:00:00Z");
        var event = new SafeRangeRestored(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), EnvironmentalVariable.HUMIDITY, new BigDecimal("50.00"),
                recordedAt, recordedAt.plusMinutes(2));

        publisher.publish(event);

        verify(spring).publishEvent(same(event));
        verifyNoMoreInteractions(spring);
    }
}
