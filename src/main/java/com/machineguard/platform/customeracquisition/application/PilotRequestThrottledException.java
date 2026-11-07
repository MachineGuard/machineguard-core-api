package com.machineguard.platform.customeracquisition.application;

public class PilotRequestThrottledException extends RuntimeException {
    private final long retryAfter;

    public PilotRequestThrottledException(long retryAfter) {
        super("Too many pilot requests; retry later");
        this.retryAfter = retryAfter;
    }

    public long retryAfter() { return retryAfter; }
}
