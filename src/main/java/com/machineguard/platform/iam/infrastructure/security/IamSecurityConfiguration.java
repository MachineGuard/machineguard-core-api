package com.machineguard.platform.iam.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.machineguard.platform.iam.application.GatewayCredentialService;

@Configuration
@EnableMethodSecurity
public class IamSecurityConfiguration {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtTokenService tokens, GatewayCredentialService gateways) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/invitations/accept").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/v1/measurements","/api/v1/measurements/batch")
                            .hasAuthority("GATEWAY_INGEST")
                        .anyRequest().hasAnyRole("ADMIN","VIEWER"))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        (request, response, exception) -> response.sendError(401, "Authentication required")))
                .addFilterBefore(new JwtAuthenticationFilter(tokens), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new GatewayCredentialFilter(gateways), JwtAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
