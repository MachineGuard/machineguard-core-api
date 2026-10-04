package com.machineguard.platform.iam.infrastructure.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machineguard.platform.iam.domain.model.Role;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String issuer;
    private final long accessTokenTtlSeconds;
    private final byte[] secret;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenService(ObjectMapper objectMapper, Clock clock,
                           @Value("${machineguard.iam.jwt-secret:}") String secret,
                           @Value("${machineguard.iam.issuer:machineguard-iam}") String issuer,
                           @Value("${machineguard.iam.access-token-ttl-seconds:900}") long accessTokenTtlSeconds) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    @PostConstruct
    void validateConfiguration() {
        if (secret.length < 32) {
            throw new IllegalStateException("IAM_JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        if (accessTokenTtlSeconds < 60 || accessTokenTtlSeconds > 3600) {
            throw new IllegalStateException("IAM access-token TTL must be between 60 and 3600 seconds");
        }
    }

    public IssuedAccessToken issueAccessToken(UUID userId, UUID organizationId, Role role, UUID sessionId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);
        Map<String, Object> claims = Map.of(
                "iss", issuer,
                "sub", userId.toString(),
                "organizationId", organizationId.toString(),
                "role", role.name(),
                "sid", sessionId.toString(),
                "iat", now.getEpochSecond(),
                "exp", expiresAt.getEpochSecond(),
                "jti", UUID.randomUUID().toString()
        );
        return new IssuedAccessToken(sign(claims), accessTokenTtlSeconds, expiresAt);
    }

    public ValidatedClaims validate(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9".equals(parts[0])) {
                throw invalidToken();
            }
            String signedContent = parts[0] + "." + parts[1];
            byte[] provided = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = mac(signedContent.getBytes(StandardCharsets.US_ASCII));
            if (!MessageDigest.isEqual(provided, expected)) throw invalidToken();

            Map<String, Object> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]),
                    new TypeReference<>() { });
            if (!issuer.equals(claims.get("iss"))) throw invalidToken();
            long expiresAt = number(claims.get("exp"));
            if (clock.instant().getEpochSecond() >= expiresAt) throw invalidToken();
            return new ValidatedClaims(UUID.fromString(string(claims.get("sub"))),
                    UUID.fromString(string(claims.get("organizationId"))), Role.valueOf(string(claims.get("role"))),
                    UUID.fromString(string(claims.get("sid"))), Instant.ofEpochSecond(expiresAt));
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw invalidToken();
        }
    }

    public String newRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String newInvitationToken() { return newRefreshToken(); }

    public static String tokenHash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String sign(Map<String, Object> claims) {
        try {
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(claims));
            String content = header + "." + payload;
            return content + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac(content.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not issue JWT", e);
        }
    }

    private byte[] mac(byte[] content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(content);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign JWT", e);
        }
    }

    private static String string(Object value) {
        if (value instanceof String s) return s;
        throw invalidToken();
    }

    private static long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        throw invalidToken();
    }

    private static BadCredentialsException invalidToken() {
        return new BadCredentialsException("Invalid or expired bearer token");
    }

    public record IssuedAccessToken(String value, long expiresIn, Instant expiresAt) { }
    public record ValidatedClaims(UUID userId, UUID organizationId, Role role, UUID sessionId, Instant expiresAt) { }
}
