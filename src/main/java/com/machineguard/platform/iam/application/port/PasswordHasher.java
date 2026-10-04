package com.machineguard.platform.iam.application.port;

/** Application port for hashing and verifying user passwords. */
public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String passwordHash);
}
