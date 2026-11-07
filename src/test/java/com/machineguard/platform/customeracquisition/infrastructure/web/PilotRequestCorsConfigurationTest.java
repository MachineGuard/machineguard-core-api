package com.machineguard.platform.customeracquisition.infrastructure.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PilotRequestCorsConfigurationTest {
    @Test void emptyConfigurationDisablesCors() {
        assertEquals(List.of(), PilotRequestCorsConfiguration.parse(""));
        assertEquals(List.of(), PilotRequestCorsConfiguration.parse("   "));
        assertEquals(List.of(), PilotRequestCorsConfiguration.parse(null));
    }

    @Test void acceptsOnlyExactOrigins() {
        assertEquals(List.of("https://landing.example.test", "http://localhost:5500"),
                PilotRequestCorsConfiguration.parse(" https://landing.example.test , http://localhost:5500 "));
    }

    @Test void rejectsWildcardsPathsAndOtherSchemes() {
        for (String unsafe : List.of("*", "https://*.example.test", "https://landing.example.test/",
                "https://landing.example.test/path", "ftp://landing.example.test", "landing.example.test")) {
            assertThrows(IllegalStateException.class, () -> PilotRequestCorsConfiguration.parse(unsafe), unsafe);
        }
    }
}
