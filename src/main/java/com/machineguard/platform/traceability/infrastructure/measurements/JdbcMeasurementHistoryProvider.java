package com.machineguard.platform.traceability.infrastructure.measurements;

import com.machineguard.platform.traceability.application.ports.MeasurementHistoryProvider;
import com.machineguard.platform.traceability.domain.exceptions.MeasurementHistoryUnavailableException;
import com.machineguard.platform.traceability.domain.model.EnvironmentalVariable;
import com.machineguard.platform.traceability.infrastructure.config.TraceabilityProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lee el Measurement History de la tabla de Environmental Monitoring. Contrato esperado de la tabla
 * (nombre configurable con machineguard.traceability.measurements-table):
 * organization_id, monitoring_zone_id, monitoring_point_id, environmental_variable, measured_value, recorded_at.
 */
@Component
public class JdbcMeasurementHistoryProvider implements MeasurementHistoryProvider {

    private final JdbcTemplate jdbc;
    private final String table;

    public JdbcMeasurementHistoryProvider(JdbcTemplate jdbc, TraceabilityProperties properties) {
        this.jdbc = jdbc;
        this.table = properties.measurementsTable();
    }

    @Override
    public List<MeasurementPoint> findByZone(UUID organizationId, UUID monitoringZoneId,
                                             EnvironmentalVariable variable, Instant from, Instant to) {
        StringBuilder sql = new StringBuilder(
                "SELECT monitoring_point_id, environmental_variable, measured_value, recorded_at FROM " + table
                        + " WHERE organization_id = ? AND monitoring_zone_id = ? AND recorded_at >= ? AND recorded_at <= ?");
        List<Object> args = new ArrayList<>(List.of(organizationId, monitoringZoneId, Timestamp.from(from), Timestamp.from(to)));
        if (variable != null) {
            sql.append(" AND environmental_variable = ?");
            args.add(variable.name());
        }
        sql.append(" ORDER BY recorded_at, monitoring_point_id");
        try {
            return jdbc.query(sql.toString(), (rs, i) -> new MeasurementPoint(
                    rs.getObject("monitoring_point_id", UUID.class),
                    EnvironmentalVariable.valueOf(rs.getString("environmental_variable")),
                    rs.getDouble("measured_value"),
                    rs.getTimestamp("recorded_at").toInstant()), args.toArray());
        } catch (DataAccessException e) {
            throw new MeasurementHistoryUnavailableException(
                    "No se pudo leer el Measurement History de Environmental Monitoring", e);
        }
    }
}
