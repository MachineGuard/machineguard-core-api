package com.machineguard.platform.iam.infrastructure.security;

import java.util.Set;

/** Every unsafe human request requires a current session, including newly added routes. */
public final class WriteSessionPolicy {
    private WriteSessionPolicy() { }
    public static final Set<String> PUBLIC_POSTS=Set.of("/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/auth/invitations/accept");
    public static final Set<String> GATEWAY_POSTS=Set.of("/api/v1/measurements","/api/v1/measurements/batch");
    public static boolean requiresHumanSession(String method,String path) {
        if(Set.of("GET","HEAD","OPTIONS").contains(method))return false;
        return !("POST".equals(method) && (PUBLIC_POSTS.contains(path) || GATEWAY_POSTS.contains(path)));
    }
}
