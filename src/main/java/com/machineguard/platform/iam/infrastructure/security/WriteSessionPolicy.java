package com.machineguard.platform.iam.infrastructure.security;

import java.util.Set;
import java.util.regex.Pattern;

/** Every unsafe human request requires a current session, including newly added routes. */
public final class WriteSessionPolicy {
    private WriteSessionPolicy() { }
    public static final Set<String> PUBLIC_POSTS=Set.of("/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/auth/invitations/accept",
            "/api/v1/pilot-requests");
    public static final Set<String> GATEWAY_POSTS=Set.of("/api/v1/measurements","/api/v1/measurements/batch");
    /** Operator decisions are authenticated by X-Operator-Key and authorized only with the OPERATOR authority. */
    private static final Pattern OPERATOR_DECISION=Pattern.compile("^/api/v1/operator/pilot-requests/[^/]+/(approve|reject)$");
    public static boolean requiresHumanSession(String method,String path) {
        if(Set.of("GET","HEAD","OPTIONS").contains(method))return false;
        return !("POST".equals(method) && (PUBLIC_POSTS.contains(path) || GATEWAY_POSTS.contains(path) || isOperatorDecision(path)));
    }
    public static boolean isOperatorDecision(String path) { return OPERATOR_DECISION.matcher(path).matches(); }
}
