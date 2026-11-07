package com.machineguard.platform.iam.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Guards /api/v1/operator/** with X-Operator-Key. Disabled operator routes answer 404. */
public class OperatorKeyFilter extends OncePerRequestFilter {
    static final String PREFIX = "/api/v1/operator/";
    static final String HEADER = "X-Operator-Key";
    private final OperatorKeyAuthenticator operators;

    public OperatorKeyFilter(OperatorKeyAuthenticator operators) { this.operators = operators; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!operators.enabled()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (request.getHeader("Authorization") != null) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Use one credential type per request");
            return;
        }
        if (!operators.matches(request.getHeader(HEADER))) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Valid operator key required");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new OperatorPrincipal(), null, List.of(new SimpleGrantedAuthority("OPERATOR"))));
        chain.doFilter(request, response);
    }
}
