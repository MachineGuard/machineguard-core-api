package com.machineguard.platform.iam.infrastructure.integration;

import com.machineguard.platform.iam.application.port.GatewaySensorOwnership;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class MonitoringGatewaySensorOwnership implements GatewaySensorOwnership {
    private final JdbcTemplate jdbc;
    public MonitoringGatewaySensorOwnership(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean belongsToOrganization(UUID sensorNodeId, UUID organizationId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM sensor_nodes s
                JOIN monitoring_points p ON p.id=s.monitoring_point_id
                JOIN monitoring_zones z ON z.id=p.monitoring_zone_id
                WHERE s.id=? AND z.organization_id=?
                """, Long.class, sensorNodeId, organizationId) == 1;
    }
}
