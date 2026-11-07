package com.machineguard.platform.customeracquisition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={
        "spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:pilotcors;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}",
        "machineguard.acquisition.cors-allowed-origins=https://landing.example.test",
        "machineguard.acquisition.pilot-ip-limit=1000"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PilotRequestCorsIntegrationTest {
    static final String LANDING = "https://landing.example.test";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    /** Pending requests reference organizations; remove them so other classes can clear organizations. */
    @AfterEach void removePilotRequests() { jdbc.update("DELETE FROM pilot_requests"); }

    @Test void preflightIsAllowedOnlyForTheConfiguredLandingOriginAndOnlyForThePilotRoute() throws Exception {
        var allowed = mvc.perform(options("/api/v1/pilot-requests").header(HttpHeaders.ORIGIN, LANDING)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")).andReturn().getResponse();
        assertEquals(200, allowed.getStatus());
        assertEquals(LANDING, allowed.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertTrue(allowed.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS).contains("POST"));

        var foreign = mvc.perform(options("/api/v1/pilot-requests").header(HttpHeaders.ORIGIN, "https://evil.example.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")).andReturn().getResponse();
        assertEquals(403, foreign.getStatus());
        assertNull(foreign.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        var otherRoute = mvc.perform(options("/api/v1/auth/login").header(HttpHeaders.ORIGIN, LANDING)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")).andReturn().getResponse();
        assertNull(otherRoute.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test void actualPilotPostFromTheLandingOriginReceivesTheCorsHeader() throws Exception {
        var response = mvc.perform(post("/api/v1/pilot-requests").header(HttpHeaders.ORIGIN, LANDING)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"companyName":"Almacen Cors SAC","contactName":"Luis Ruiz","contactEmail":"cors-%s@landing.example.test",
                         "contactPhone":"+51 900 111 222","industry":"PHARMA","estimatedMonthlyLoss":null}
                        """.formatted(java.util.UUID.randomUUID()))).andReturn().getResponse();
        assertEquals(202, response.getStatus());
        assertEquals(LANDING, response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
