package com.machineguard.platform.environmentalmonitoring.infrastructure.integration;

import com.machineguard.platform.environmentalmonitoring.application.ports.GatewayDeviceResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcGatewayDeviceResolver implements GatewayDeviceResolver {
    private final JdbcTemplate jdbc;
    public JdbcGatewayDeviceResolver(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public Optional<Binding> resolve(UUID organizationId,String deviceCode) {
        return jdbc.query("""
                SELECT z.id AS zone_id, p.id AS point_id, s.id AS sensor_id
                FROM sensor_nodes s JOIN monitoring_points p ON p.id=s.monitoring_point_id
                JOIN monitoring_zones z ON z.id=p.monitoring_zone_id
                WHERE z.organization_id=? AND s.device_code=?
                  AND z.status='ACTIVE' AND p.status='ACTIVE' AND s.status<>'INACTIVE'
                """,(rs,row)->new Binding(rs.getObject("zone_id",UUID.class),rs.getObject("point_id",UUID.class),
                rs.getObject("sensor_id",UUID.class)),organizationId,deviceCode.trim()).stream().findFirst();
    }
}
