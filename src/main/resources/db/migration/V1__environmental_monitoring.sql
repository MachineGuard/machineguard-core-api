-- Bounded Context: Environmental Monitoring
-- organization_id y facility_id son referencias logicas hacia otros Bounded Contexts
-- (no son Foreign Keys fisicas). Los identificadores son UUID, igual que en IAM.
-- measurements usa un registro por variable ambiental (TEMPERATURE / HUMIDITY): es la forma
-- que lee Traceability & Quality para construir el Measurement History.

CREATE TABLE monitoring_zones (
    id              UUID PRIMARY KEY,
    organization_id UUID                     NOT NULL,
    facility_id     UUID,
    name            VARCHAR(150)             NOT NULL,
    description     VARCHAR(500),
    status          VARCHAR(20)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_monitoring_zones_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_monitoring_zones_org_status ON monitoring_zones (organization_id, status);

CREATE TABLE monitoring_points (
    id                 UUID PRIMARY KEY,
    monitoring_zone_id UUID                     NOT NULL REFERENCES monitoring_zones (id),
    name               VARCHAR(150)             NOT NULL,
    location           VARCHAR(250),
    status             VARCHAR(20)              NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_monitoring_points_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_monitoring_points_zone ON monitoring_points (monitoring_zone_id);

CREATE TABLE sensor_nodes (
    id                        UUID PRIMARY KEY,
    monitoring_point_id       UUID                     NOT NULL REFERENCES monitoring_points (id),
    device_code               VARCHAR(100)             NOT NULL,
    sampling_interval_seconds INTEGER                  NOT NULL,
    status                    VARCHAR(20)              NOT NULL,
    last_measurement_at       TIMESTAMP WITH TIME ZONE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_sensor_nodes_device_code UNIQUE (device_code),
    CONSTRAINT ck_sensor_nodes_status CHECK (status IN ('ONLINE', 'OFFLINE', 'INACTIVE')),
    CONSTRAINT ck_sensor_nodes_interval CHECK (sampling_interval_seconds > 0)
);

CREATE INDEX idx_sensor_nodes_point ON sensor_nodes (monitoring_point_id);

CREATE TABLE thresholds (
    id                     UUID PRIMARY KEY,
    monitoring_zone_id     UUID                     NOT NULL REFERENCES monitoring_zones (id),
    environmental_variable VARCHAR(20)              NOT NULL,
    minimum_value          NUMERIC(6, 2)            NOT NULL,
    maximum_value          NUMERIC(6, 2)            NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_thresholds_zone_variable UNIQUE (monitoring_zone_id, environmental_variable),
    CONSTRAINT ck_thresholds_variable CHECK (environmental_variable IN ('TEMPERATURE', 'HUMIDITY')),
    CONSTRAINT ck_thresholds_range CHECK (minimum_value < maximum_value)
);

CREATE TABLE measurements (
    id                     UUID PRIMARY KEY,
    organization_id        UUID                     NOT NULL,
    monitoring_zone_id     UUID                     NOT NULL REFERENCES monitoring_zones (id),
    monitoring_point_id    UUID                     NOT NULL REFERENCES monitoring_points (id),
    sensor_node_id         UUID                     REFERENCES sensor_nodes (id),
    environmental_variable VARCHAR(20)              NOT NULL,
    measured_value         NUMERIC(6, 2)            NOT NULL,
    recorded_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_measurements_variable CHECK (environmental_variable IN ('TEMPERATURE', 'HUMIDITY'))
);

CREATE INDEX idx_measurements_zone_recorded ON measurements (organization_id, monitoring_zone_id, recorded_at);
CREATE INDEX idx_measurements_point_recorded ON measurements (monitoring_point_id, recorded_at);
CREATE INDEX idx_measurements_node ON measurements (sensor_node_id);
