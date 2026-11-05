package com.machineguard.platform.iam.application;

public final class LoginThrottledException extends RuntimeException {
    private final long retryAfter;
    public LoginThrottledException(long retryAfter) { super("Login rate limit reached");this.retryAfter=retryAfter; }
    public long retryAfter() { return retryAfter; }
}
