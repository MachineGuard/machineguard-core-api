package com.machineguard.platform.traceability.application.eventhandlers;

import com.machineguard.platform.traceability.domain.model.RetentionPeriod;
import com.machineguard.platform.traceability.domain.services.RetentionPolicyService;
import com.machineguard.platform.traceability.infrastructure.config.TraceabilityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Depuracion diaria opcional (machineguard.traceability.retention-purge-enabled=true). */
@Component
@ConditionalOnProperty(prefix = "machineguard.traceability", name = "retention-purge-enabled", havingValue = "true")
public class RetentionPurgeJob {

    private final RetentionPolicyService retention;
    private final TraceabilityProperties properties;

    public RetentionPurgeJob(RetentionPolicyService retention, TraceabilityProperties properties) {
        this.retention = retention;
        this.properties = properties;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    public void purge() {
        retention.purgeExpired(new RetentionPeriod(properties.retentionMonths()));
    }
}
