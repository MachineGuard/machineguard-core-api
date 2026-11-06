ALTER TABLE measurements ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'LEGACY_UNKNOWN';
ALTER TABLE measurements ADD CONSTRAINT ck_measurement_source CHECK (source IN ('SENSOR','MANUAL','LEGACY_UNKNOWN'));
CREATE INDEX ix_measurement_sensor_capture ON measurements(organization_id, monitoring_zone_id, source, sensor_node_id, environmental_variable, recorded_at);
CREATE INDEX ix_measurement_sensor_receipt ON measurements(organization_id, monitoring_zone_id, source, sensor_node_id, received_at);
