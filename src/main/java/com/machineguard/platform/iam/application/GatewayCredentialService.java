package com.machineguard.platform.iam.application;

import com.machineguard.platform.iam.application.port.GatewaySensorOwnership;
import com.machineguard.platform.iam.application.port.TokenProvider;
import com.machineguard.platform.iam.domain.model.*;
import com.machineguard.platform.iam.domain.port.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class GatewayCredentialService {
    private final GatewayRepository gateways;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final GatewaySensorOwnership sensors;
    private final TokenProvider tokens;
    private final Clock clock;
    public GatewayCredentialService(GatewayRepository gateways, OrganizationRepository organizations, UserRepository users,
            GatewaySensorOwnership sensors, TokenProvider tokens, Clock clock) {
        this.gateways=gateways; this.organizations=organizations; this.users=users; this.sensors=sensors;
        this.tokens=tokens; this.clock=clock;
    }
    public record IssuedCredential(Gateway gateway, String credential) {
        @Override public String toString() { return "IssuedCredential[gateway="+gateway.id()+", credential=REDACTED]"; }
    }
    public record Identity(UUID gatewayId, UUID organizationId, Set<UUID> sensorNodeIds) {
        public Identity { sensorNodeIds=Set.copyOf(sensorNodeIds); }
    }

    @Transactional
    public IssuedCredential register(UUID org, UUID actor, String name, Set<UUID> sensorNodeIds, Integer ttlDays) {
        requireAdministrator(org,actor);
        if (sensorNodeIds == null || sensorNodeIds.isEmpty() || sensorNodeIds.size()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select 1 to 100 sensors");
        for (UUID sensor : sensorNodeIds)
            if (!sensors.belongsToOrganization(sensor,org)) throw notFound();
        UUID id=UUID.randomUUID(); String raw=credential(id); Instant now=clock.instant();
        Gateway saved=gateways.save(new Gateway(id,org,name,tokens.hashToken(raw),sensorNodeIds,actor,actor,now,now,
                expiry(now,ttlDays),null));
        return new IssuedCredential(saved,raw);
    }
    @Transactional(readOnly=true)
    public List<Gateway> list(UUID org, UUID actor) { requireAdministrator(org,actor); return gateways.findByOrganizationId(org); }
    @Transactional(readOnly=true)
    public Gateway get(UUID org, UUID actor, UUID id) {
        requireAdministrator(org,actor); return gateways.findByIdAndOrganizationId(id,org).orElseThrow(this::notFound);
    }
    @Transactional
    public IssuedCredential rotate(UUID org, UUID actor, UUID id, Integer ttlDays) {
        requireAdministrator(org,actor);
        Gateway gateway=gateways.lockByIdAndOrganizationId(id,org).orElseThrow(this::notFound);
        if (gateway.revokedAt()!=null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Gateway was revoked; register a new identity");
        String raw=credential(id); Instant now=clock.instant();
        return new IssuedCredential(gateways.save(gateway.rotate(tokens.hashToken(raw),expiry(now,ttlDays),actor,now)),raw);
    }
    @Transactional
    public Gateway revoke(UUID org, UUID actor, UUID id) {
        requireAdministrator(org,actor);
        return gateways.save(gateways.lockByIdAndOrganizationId(id,org).orElseThrow(this::notFound).revoke(actor,clock.instant()));
    }
    @Transactional(readOnly=true)
    public Identity authenticate(String raw) {
        try {
            if (raw==null || raw.length()>200 || !raw.startsWith("mgg_")) throw new GatewayAuthenticationException();
            String[] parts=raw.split("\\.",-1);
            if (parts.length!=2 || parts[1].isBlank()) throw new GatewayAuthenticationException();
            UUID id=UUID.fromString(parts[0].substring(4));
            Gateway gateway=gateways.findById(id).orElseThrow(GatewayAuthenticationException::new);
            if (!gateway.isActiveAt(clock.instant()) || !MessageDigest.isEqual(
                    gateway.credentialHash().getBytes(StandardCharsets.US_ASCII),
                    tokens.hashToken(raw).getBytes(StandardCharsets.US_ASCII))) throw new GatewayAuthenticationException();
            Organization organization=organizations.findById(gateway.organizationId()).orElseThrow(GatewayAuthenticationException::new);
            if (organization.getStatus()!=OrganizationStatus.ACTIVE) throw new GatewayAuthenticationException();
            return new Identity(id,gateway.organizationId(),gateway.sensorNodeIds());
        } catch (IllegalArgumentException e) { throw new GatewayAuthenticationException(); }
    }
    private void requireAdministrator(UUID org, UUID actor) {
        var user=users.findByIdAndOrganizationId(actor,org).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,"Active administrator required"));
        var organization=organizations.findById(org).orElseThrow(this::notFound);
        if (user.getRole()!=Role.ADMIN || user.getStatus()!=UserStatus.ACTIVE || organization.getStatus()!=OrganizationStatus.ACTIVE)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Active administrator required");
    }
    private String credential(UUID id) { return "mgg_"+id+"."+tokens.newRefreshToken(); }
    private Instant expiry(Instant now, Integer requested) {
        int days=requested==null ? 30 : requested;
        if (days<1 || days>90) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Credential TTL must be 1 to 90 days");
        return now.plusSeconds(days*86_400L);
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Gateway or sensor not found"); }
}
