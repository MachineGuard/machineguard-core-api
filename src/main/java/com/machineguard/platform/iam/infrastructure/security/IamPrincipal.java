package com.machineguard.platform.iam.infrastructure.security;

import com.machineguard.platform.iam.domain.model.Role;

import java.util.UUID;
import java.time.Instant;

public record IamPrincipal(UUID userId, UUID organizationId, Role role, UUID sessionId, Instant expiresAt) { }
