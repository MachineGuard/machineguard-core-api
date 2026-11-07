package com.machineguard.platform.iam.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Operator key for global pilot approval. Only the SHA-256 hash is configured; the raw key is never stored.
 * Disabled when no hash is configured. This is not a human user, not a role and not a JWT claim.
 */
@Component
public class OperatorKeyAuthenticator {
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final int MAX_KEY_LENGTH = 512;
    private final String configuredHash;

    public OperatorKeyAuthenticator(@Value("${machineguard.iam.operator-key-hash:}") String configuredHash) {
        String value = configuredHash == null ? "" : configuredHash.trim();
        if (!value.isEmpty() && !SHA256_HEX.matcher(value).matches()) {
            throw new IllegalStateException("IAM_OPERATOR_KEY_HASH must be a lowercase SHA-256 hex digest");
        }
        this.configuredHash = value;
    }

    public boolean enabled() { return !configuredHash.isEmpty(); }

    public boolean matches(String presentedKey) {
        if (!enabled() || presentedKey == null || presentedKey.isEmpty() || presentedKey.length() > MAX_KEY_LENGTH) {
            return false;
        }
        byte[] presented = JwtTokenService.tokenHash(presentedKey).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(presented, configuredHash.getBytes(StandardCharsets.US_ASCII));
    }
}
