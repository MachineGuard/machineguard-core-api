package com.machineguard.platform.iam.infrastructure.persistence.repository;

import com.machineguard.platform.iam.infrastructure.persistence.BootstrapStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BootstrapStateJpaRepository extends JpaRepository<BootstrapStateEntity, String> { }
