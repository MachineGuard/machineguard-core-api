package com.machineguard.platform.iam;

import com.machineguard.platform.iam.domain.model.Gateway;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GatewayDomainTest {
    private final Instant now=Instant.parse("2026-10-30T12:00:00Z");
    private Gateway gateway() {
        UUID actor=UUID.randomUUID();
        return new Gateway(UUID.randomUUID(),UUID.randomUUID()," Warehouse ","a".repeat(64),Set.of(UUID.randomUUID()),
                actor,actor,now,now,now.plusSeconds(3600),null);
    }
    @Test void activeCredentialExpiresAtTheExactDeadline() {
        Gateway g=gateway(); assertEquals("Warehouse",g.name());
        assertTrue(g.isActiveAt(now)); assertFalse(g.isActiveAt(g.expiresAt()));
    }
    @Test void revocationIsIdempotentAndRotationCannotReactivateIt() {
        Gateway g=gateway().revoke(UUID.randomUUID(),now.plusSeconds(10));
        assertFalse(g.isActiveAt(now.plusSeconds(11)));
        assertSame(g,g.revoke(UUID.randomUUID(),now.plusSeconds(20)));
        assertThrows(IllegalStateException.class,()->g.rotate("b".repeat(64),now.plusSeconds(5000),UUID.randomUUID(),now));
    }
    @Test void rotationPreservesIdentityAndSensorScopeAndRedactsItsHash() {
        Gateway first=gateway(); UUID actor=UUID.randomUUID();
        Gateway next=first.rotate("b".repeat(64),now.plusSeconds(7200),actor,now.plusSeconds(20));
        assertEquals(first.id(),next.id()); assertEquals(first.organizationId(),next.organizationId());
        assertEquals(first.sensorNodeIds(),next.sensorNodeIds()); assertEquals(actor,next.updatedBy());
        assertFalse(next.toString().contains(next.credentialHash()));
    }
}
