package com.machineguard.platform.iam.infrastructure.security;

import com.machineguard.platform.iam.application.GatewayAuthenticationException;
import com.machineguard.platform.iam.application.GatewayCredentialService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

public class GatewayCredentialFilter extends OncePerRequestFilter {
    private final GatewayCredentialService gateways;
    public GatewayCredentialFilter(GatewayCredentialService gateways) { this.gateways=gateways; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String credential=request.getHeader("X-Device-Credential");
        if (credential!=null) {
            if (request.getHeader("Authorization")!=null) { GatewaySecurityResponse.reject(request,response,401,"Use one credential type per request"); return; }
            try {
                var identity=gateways.authenticate(credential);
                var principal=new GatewayPrincipal(identity.gatewayId(),identity.organizationId(),identity.sensorNodeIds());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,
                        List.of(new SimpleGrantedAuthority("GATEWAY_INGEST"))));
            } catch (GatewayAuthenticationException e) {
                SecurityContextHolder.clearContext(); GatewaySecurityResponse.reject(request,response,401,"Invalid or expired gateway credential"); return;
            }
        }
        chain.doFilter(request,response);
    }
}
