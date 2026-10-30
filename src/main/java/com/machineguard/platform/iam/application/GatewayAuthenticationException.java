package com.machineguard.platform.iam.application;

public class GatewayAuthenticationException extends RuntimeException {
    public GatewayAuthenticationException() { super("Invalid or expired gateway credential"); }
}
