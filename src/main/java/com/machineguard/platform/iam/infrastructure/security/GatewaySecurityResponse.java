package com.machineguard.platform.iam.infrastructure.security;

import jakarta.servlet.http.*;
import java.io.IOException;

final class GatewaySecurityResponse {
    private GatewaySecurityResponse() { }
    static boolean ingestion(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && ("/api/v1/measurements".equals(request.getRequestURI())
                || "/api/v1/measurements/batch".equals(request.getRequestURI()));
    }
    static void reject(HttpServletRequest request,HttpServletResponse response,int status,String detail) throws IOException {
        if (!ingestion(request)) { response.sendError(status,detail); return; }
        response.setStatus(status); response.setContentType("application/problem+json");
        String code=status==401?"GATEWAY_CREDENTIAL_REJECTED":"GATEWAY_INGEST_FORBIDDEN";
        response.getWriter().write("{\"status\":"+status+",\"code\":\""+code+"\"}");
    }
}
