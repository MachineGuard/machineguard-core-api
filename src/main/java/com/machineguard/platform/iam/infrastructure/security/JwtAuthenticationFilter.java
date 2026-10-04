package com.machineguard.platform.iam.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokens;

    public JwtAuthenticationFilter(JwtTokenService tokens) { this.tokens = tokens; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer authorization");
                return;
            }
            try {
                JwtTokenService.ValidatedClaims claims = tokens.validate(authorization.substring(7));
                IamPrincipal principal = new IamPrincipal(claims.userId(), claims.organizationId(), claims.role(),
                        claims.sessionId(), claims.expiresAt());
                var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BadCredentialsException e) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired bearer token");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
