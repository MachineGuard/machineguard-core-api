package com.machineguard.platform.iam.application;

public class RefreshTokenReplayException extends RuntimeException {
    public RefreshTokenReplayException() { super("Refresh token reuse detected; the session was revoked"); }
}
