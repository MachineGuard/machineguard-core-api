package com.machineguard.platform.customeracquisition;

import com.machineguard.platform.customeracquisition.domain.model.Industry;
import com.machineguard.platform.customeracquisition.domain.model.PilotRequest;
import com.machineguard.platform.customeracquisition.domain.model.PilotRequestStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PilotRequestDomainTest {
    static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    static PilotRequest submit(String company, String email, String phone, BigDecimal loss) {
        return PilotRequest.submit(UUID.randomUUID(), company, "Ana Perez", email, phone, Industry.FOOD, loss, NOW);
    }

    @Test void submitTrimsAndNormalizesContactAndRoundsLossToCents() {
        var request = submit("  Almacen Norte  ", " Ana@Example.TEST ", "+51 987 654 321", new BigDecimal("1250.555"));
        assertEquals("Almacen Norte", request.companyName());
        assertEquals("ana@example.test", request.contactEmail());
        assertEquals(new BigDecimal("1250.56"), request.estimatedMonthlyLoss());
        assertEquals(PilotRequestStatus.PENDING, request.status());
        assertNull(request.organizationId());
        assertNull(request.decidedAt());
    }

    @Test void optionalLossMayBeAbsent() {
        assertNull(submit("Almacen", "ana@example.test", "+51 987 654 321", null).estimatedMonthlyLoss());
    }

    @Test void rejectsInvalidValuesAtTheDomainBoundary() {
        assertThrows(IllegalArgumentException.class, () -> submit("Almacen", "not-an-email", "+51 987 654 321", null));
        assertThrows(IllegalArgumentException.class, () -> submit("Almacen", "ana@example.test", "12ab", null));
        assertThrows(IllegalArgumentException.class, () -> submit("   ", "ana@example.test", "+51 987 654 321", null));
        assertThrows(IllegalArgumentException.class, () -> submit("a".repeat(151), "ana@example.test", "+51 987 654 321", null));
        assertThrows(IllegalArgumentException.class, () -> submit("Almacen", "ana@example.test", "+51 987 654 321", new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class, () -> submit("Almacen", "ana@example.test", "+51 987 654 321", new BigDecimal("1000000000000")));
    }

    @Test void onlyPendingRequestsCanBeDecidedAndOnlyOnce() {
        var approved = submit("Almacen", "ana@example.test", "+51 987 654 321", null);
        approved.approve(ORG, NOW);
        assertEquals(PilotRequestStatus.APPROVED, approved.status());
        assertEquals(ORG, approved.organizationId());
        assertThrows(IllegalStateException.class, () -> approved.approve(ORG, NOW));
        assertThrows(IllegalStateException.class, () -> approved.reject(NOW));

        var rejected = submit("Almacen", "ana@example.test", "+51 987 654 321", null);
        rejected.reject(NOW);
        assertEquals(PilotRequestStatus.REJECTED, rejected.status());
        assertThrows(IllegalStateException.class, () -> rejected.approve(ORG, NOW));
    }
}
