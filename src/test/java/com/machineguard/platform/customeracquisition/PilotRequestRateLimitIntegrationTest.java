package com.machineguard.platform.customeracquisition;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties={
        "spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:pilotratelimit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}",
        "machineguard.acquisition.pilot-ip-limit=2"})
@AutoConfigureMockMvc
class PilotRequestRateLimitIntegrationTest {
    @Autowired MockMvc mvc;

    /** Each request has a fresh email, so only the address window can change the outcome. */
    static MockHttpServletRequestBuilder pilotPost(String remoteAddress, String forwardedFor) {
        return post("/api/v1/pilot-requests").with(request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        }).header("X-Forwarded-For", forwardedFor).contentType(MediaType.APPLICATION_JSON).content("""
                {"companyName":"Limite SAC","contactName":"Eva Lopez","contactEmail":"limit-%s@landing.example.test",
                 "contactPhone":"+51 900 333 444","industry":"OTHER","estimatedMonthlyLoss":10}
                """.formatted(UUID.randomUUID()));
    }

    @Test void perAddressLimitIgnoresForwardedHeadersAndReturnsRetryAfter() throws Exception {
        assertEquals(202, mvc.perform(pilotPost("198.51.100.10", "203.0.113.1")).andReturn().getResponse().getStatus());
        assertEquals(202, mvc.perform(pilotPost("198.51.100.10", "203.0.113.2")).andReturn().getResponse().getStatus());
        var throttled = mvc.perform(pilotPost("198.51.100.10", "203.0.113.3")).andReturn().getResponse();
        assertEquals(429, throttled.getStatus());
        int retry = Integer.parseInt(throttled.getHeader("Retry-After"));
        assertTrue(retry >= 1 && retry <= 3600, "Retry-After=" + retry);
        assertEquals(202, mvc.perform(pilotPost("198.51.100.11", "203.0.113.4")).andReturn().getResponse().getStatus(),
                "another client address is not throttled");
    }
}
