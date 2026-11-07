package com.machineguard.platform.customeracquisition;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Public Landing intake with the operator key disabled (default configuration). */
@SpringBootTest(properties={
        "spring.datasource.url=${MACHINEGUARD_TEST_DB_URL:jdbc:h2:mem:pilotintake;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}",
        "machineguard.acquisition.pilot-ip-limit=1000"})
@AutoConfigureMockMvc
class PilotRequestApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;

    static String email() { return "pilot-" + UUID.randomUUID() + "@landing.example.test"; }

    /** Exact field names sent by the Landing; acceptTerms is a UI-only field and must be ignored. */
    static Map<String, Object> landingPayload(String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("companyName", "Almacen Norte SAC");
        body.put("contactName", "Ana Perez");
        body.put("contactEmail", email);
        body.put("contactPhone", "+51 987 654 321");
        body.put("industry", "FOOD");
        body.put("estimatedMonthlyLoss", 1250.5);
        body.put("acceptTerms", true);
        return body;
    }

    MvcResult submit(Object body) throws Exception {
        return mvc.perform(post("/api/v1/pilot-requests").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn();
    }

    int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? -1 : value;
    }

    @Test void validLandingPayloadIsAcceptedAsPendingWithoutCreatingATenant() throws Exception {
        String email = email();
        MvcResult response = submit(landingPayload(email));
        assertEquals(202, response.getResponse().getStatus());
        assertEquals("no-store", response.getResponse().getHeader("Cache-Control"));
        JsonNode body = json.readTree(response.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());
        assertEquals("PENDING", body.get("status").asText());
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM pilot_requests WHERE id=?", String.class, id));
        assertEquals(email, jdbc.queryForObject("SELECT pending_contact_email FROM pilot_requests WHERE id=?", String.class, id));
        assertEquals(0, count("SELECT count(*) FROM organizations WHERE acquisition_request_id=?", id));
        assertEquals(0, count("SELECT count(*) FROM users WHERE email=?", email));
    }

    @Test void repeatedPendingSubmissionReturnsTheSameRequestAndKeepsItsFirstData() throws Exception {
        String email = email();
        JsonNode first = json.readTree(submit(landingPayload(email)).getResponse().getContentAsString());
        Map<String, Object> second = landingPayload("  " + email.toUpperCase() + " ");
        second.put("companyName", "Otra empresa");
        MvcResult response = submit(second);
        assertEquals(202, response.getResponse().getStatus());
        assertEquals(first.get("id").asText(), json.readTree(response.getResponse().getContentAsString()).get("id").asText());
        assertEquals(1, count("SELECT count(*) FROM pilot_requests WHERE pending_contact_email=?", email));
        assertEquals("Almacen Norte SAC", jdbc.queryForObject(
                "SELECT company_name FROM pilot_requests WHERE pending_contact_email=?", String.class, email));
    }

    @Test void invalidPayloadsAreRejectedWithoutPersistence() throws Exception {
        Map<String, Object> missingCompany = landingPayload(email());
        missingCompany.put("companyName", null);
        Map<String, Object> badEmail = landingPayload("not-an-email");
        Map<String, Object> badPhone = landingPayload(email());
        badPhone.put("contactPhone", "12ab");
        Map<String, Object> unknownIndustry = landingPayload(email());
        unknownIndustry.put("industry", "MINING");
        Map<String, Object> missingIndustry = landingPayload(email());
        missingIndustry.remove("industry");
        Map<String, Object> negativeLoss = landingPayload(email());
        negativeLoss.put("estimatedMonthlyLoss", -1);
        Map<String, Object> hugeLoss = landingPayload(email());
        hugeLoss.put("estimatedMonthlyLoss", 1_000_000_000_000L);
        Map<String, Object> longCompany = landingPayload(email());
        longCompany.put("companyName", "a".repeat(151));
        int before = count("SELECT count(*) FROM pilot_requests");
        for (Map<String, Object> invalid : java.util.List.of(missingCompany, badEmail, badPhone, unknownIndustry,
                missingIndustry, negativeLoss, hugeLoss, longCompany)) {
            assertEquals(400, submit(invalid).getResponse().getStatus(), invalid.toString());
        }
        assertEquals(before, count("SELECT count(*) FROM pilot_requests"));
        MvcResult malformed = mvc.perform(post("/api/v1/pilot-requests").contentType(MediaType.APPLICATION_JSON).content("{")).andReturn();
        assertEquals(400, malformed.getResponse().getStatus());
    }

    @Test void onlyTheLandingPilotPostIsPublic() throws Exception {
        assertEquals(401, mvc.perform(get("/api/v1/pilot-requests")).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(post("/api/v1/pilot-requests/extra")).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(post("/api/v1/pilot-requests/" + UUID.randomUUID() + "/approve"))
                .andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(post("/api/v1/gateways").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus());
    }

    @Test void operatorRoutesAreDisabledWithoutAConfiguredKey() throws Exception {
        assertEquals(404, mvc.perform(get("/api/v1/operator/pilot-requests")).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(post("/api/v1/operator/pilot-requests/" + UUID.randomUUID() + "/approve")
                .header("X-Operator-Key", "anything")).andReturn().getResponse().getStatus());
    }
}
