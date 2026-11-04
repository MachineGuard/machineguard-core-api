package com.machineguard.platform.environmentalmonitoring.domain.exceptions;

public class EnvironmentalMonitoringRuleException extends RuntimeException {
    public enum Reason { OTHER, READING_CONFLICT, NODE_INACTIVE, THRESHOLD_NOT_CONFIGURED }
    private final Reason reason;
    public EnvironmentalMonitoringRuleException(String message) { this(Reason.OTHER, message); }
    public EnvironmentalMonitoringRuleException(Reason reason, String message) { super(message); this.reason=reason; }
    public Reason reason() { return reason; }
}
