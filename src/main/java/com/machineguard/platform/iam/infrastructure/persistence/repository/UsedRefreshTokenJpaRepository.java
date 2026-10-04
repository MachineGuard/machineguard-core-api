package com.machineguard.platform.iam.infrastructure.persistence.repository;

import com.machineguard.platform.iam.infrastructure.persistence.UsedRefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsedRefreshTokenJpaRepository extends JpaRepository<UsedRefreshTokenEntity, String> { }
