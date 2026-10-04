package com.machineguard.platform.iam.domain.port;

import com.machineguard.platform.iam.domain.model.UsedRefreshToken;

import java.util.Optional;

public interface UsedRefreshTokenRepository {
    Optional<UsedRefreshToken> findByTokenHash(String tokenHash);
    UsedRefreshToken save(UsedRefreshToken token);
}
