package com.machineguard.platform.alertincident.domain.model;

public enum AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean isHigh() {
        return this == HIGH;
    }

    public boolean isCritical() {
        return this == CRITICAL;
    }
}
