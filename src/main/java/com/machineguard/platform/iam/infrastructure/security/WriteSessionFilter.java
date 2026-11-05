package com.machineguard.platform.iam.infrastructure.security;

import com.machineguard.platform.iam.application.IamService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;

public class WriteSessionFilter extends OncePerRequestFilter {
    private final IamService iam;
    public WriteSessionFilter(IamService iam) { this.iam=iam; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException,ServletException {
        if(WriteSessionPolicy.requiresHumanSession(request.getMethod(),request.getRequestURI())) {
            var auth=SecurityContextHolder.getContext().getAuthentication();
            if(auth!=null && auth.getPrincipal() instanceof IamPrincipal principal) {
                try { iam.validateToken(principal); }
                catch(ResponseStatusException failure) {
                    SecurityContextHolder.clearContext();response.sendError(401,"A current human session is required");return;
                }
            }
            else {
                GatewaySecurityResponse.reject(request,response,auth==null?401:403,"A human session is required");return;
            }
        }
        chain.doFilter(request,response);
    }
}
