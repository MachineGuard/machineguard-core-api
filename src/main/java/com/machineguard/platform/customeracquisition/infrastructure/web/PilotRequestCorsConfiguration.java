package com.machineguard.platform.customeracquisition.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * CORS for the public pilot intake (configured Landing origins) and, when configured, for the API used by a
 * Web Application served from another origin. Each list is disabled when empty; other routes receive no CORS headers.
 */
@Configuration
public class PilotRequestCorsConfiguration {
    static final String PATH = "/api/v1/pilot-requests";
    static final String API_PATH = "/api/v1/**";
    private static final Pattern ORIGIN = Pattern.compile("^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?$");

    @Bean
    CorsConfigurationSource pilotRequestCorsSource(@Value("${machineguard.acquisition.cors-allowed-origins:}") String configured,
                                                   @Value("${machineguard.web.cors-allowed-origins:}") String webConfigured) {
        List<String> origins = parse(configured);
        var source = new UrlBasedCorsConfigurationSource();
        if (!origins.isEmpty()) {
            var cors = new CorsConfiguration();
            cors.setAllowedOrigins(origins);
            cors.setAllowedMethods(List.of("POST"));
            cors.setAllowedHeaders(List.of("Content-Type", "Accept", "Accept-Language"));
            cors.setMaxAge(600L);
            source.registerCorsConfiguration(PATH, cors);
        }
        List<String> webOrigins = parse(webConfigured);
        if (!webOrigins.isEmpty()) {
            var cors = new CorsConfiguration();
            cors.setAllowedOrigins(webOrigins);
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
            cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Accept-Language"));
            cors.setExposedHeaders(List.of("Content-Disposition"));
            cors.setMaxAge(600L);
            source.registerCorsConfiguration(API_PATH, cors);
        }
        return source;
    }

    static List<String> parse(String configured) {
        if (configured == null || configured.isBlank()) return List.of();
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .peek(origin -> {
                    if (!ORIGIN.matcher(origin).matches()) {
                        throw new IllegalStateException("CORS allowed origins must list exact http(s) origins without paths or wildcards");
                    }
                })
                .toList();
    }
}
