ALTER TABLE incidents ADD COLUMN historical BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE incidents ADD COLUMN live_started_at TIMESTAMP WITH TIME ZONE;
UPDATE incidents SET live_started_at = opened_at;

-- References to Monitoring remain logical; evidence is independent of report snapshots.
CREATE TABLE late_deviations (
    measurement_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    monitoring_zone_id UUID NOT NULL,
    monitoring_point_id UUID NOT NULL,
    sensor_node_id UUID,
    environmental_variable VARCHAR(20) NOT NULL,
    measured_value NUMERIC(6,2) NOT NULL,
    minimum_value NUMERIC(6,2) NOT NULL,
    maximum_value NUMERIC(6,2) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_late_deviations_variable CHECK (environmental_variable IN ('TEMPERATURE','HUMIDITY'))
);
CREATE INDEX idx_late_deviations_org_zone_recorded
    ON late_deviations(organization_id, monitoring_zone_id, recorded_at);
