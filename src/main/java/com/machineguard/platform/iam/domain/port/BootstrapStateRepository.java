package com.machineguard.platform.iam.domain.port;

import com.machineguard.platform.iam.domain.model.BootstrapState;

import java.util.Optional;

public interface BootstrapStateRepository {
    Optional<BootstrapState> findByKey(String key);
    BootstrapState save(BootstrapState state);
}
