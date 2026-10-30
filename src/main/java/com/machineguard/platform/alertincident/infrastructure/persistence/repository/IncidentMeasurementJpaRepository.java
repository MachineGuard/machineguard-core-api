package com.machineguard.platform.alertincident.infrastructure.persistence.repository;
import com.machineguard.platform.alertincident.domain.model.IncidentMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface IncidentMeasurementJpaRepository extends JpaRepository<IncidentMeasurement, UUID> { }
