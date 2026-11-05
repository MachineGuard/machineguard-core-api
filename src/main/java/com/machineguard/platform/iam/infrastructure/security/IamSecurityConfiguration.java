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
import com.machineguard.platform.iam.application.IamService;

@Configuration
@EnableMethodSecurity
public class IamSecurityConfiguration {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtTokenService tokens, GatewayCredentialService gateways,IamService iam) throws Exception {
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
                        (request, response, exception) -> GatewaySecurityResponse.reject(request,response,401,"Authentication required"))
                        .accessDeniedHandler((request,response,exception) -> GatewaySecurityResponse.reject(request,response,403,"Access denied")))
                .addFilterBefore(new JwtAuthenticationFilter(tokens), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new WriteSessionFilter(iam),JwtAuthenticationFilter.class)
                .addFilterBefore(new GatewayCredentialFilter(gateways), JwtAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
