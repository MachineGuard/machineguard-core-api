package com.machineguard.platform.traceability.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "machineguard.traceability")
public record TraceabilityProperties(int retentionMonths, boolean retentionPurgeEnabled, int maxReportDays,
                                     String measurementsTable) {

    public TraceabilityProperties {
        if (retentionMonths <= 0) {
            retentionMonths = 60;
        }
        if (maxReportDays <= 0) {
            maxReportDays = 366;
        }
        if (measurementsTable == null || !measurementsTable.matches("[A-Za-z_][A-Za-z0-9_.]*")) {
            measurementsTable = "measurements";
        }
    }
}
