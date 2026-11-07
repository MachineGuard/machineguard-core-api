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
 * CORS only for the public pilot intake, and only for the configured Landing origins. Disabled when empty.
 * Other routes receive no CORS headers.
 */
@Configuration
public class PilotRequestCorsConfiguration {
    static final String PATH = "/api/v1/pilot-requests";
    private static final Pattern ORIGIN = Pattern.compile("^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?$");

    @Bean
    CorsConfigurationSource pilotRequestCorsSource(@Value("${machineguard.acquisition.cors-allowed-origins:}") String configured) {
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
        return source;
    }

    static List<String> parse(String configured) {
        if (configured == null || configured.isBlank()) return List.of();
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .peek(origin -> {
                    if (!ORIGIN.matcher(origin).matches()) {
                        throw new IllegalStateException("ACQUISITION_CORS_ALLOWED_ORIGINS must list exact http(s) origins without paths or wildcards");
                    }
                })
                .toList();
    }
}
