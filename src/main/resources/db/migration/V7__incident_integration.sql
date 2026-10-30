-- Evolve published migrations without changing their checksums.
ALTER TABLE incidents RENAME COLUMN deviation_id TO origin_measurement_id;
-- Keep every absorbed measurement so a replay after resolution cannot open a new incident.
CREATE TABLE incident_measurements (
    measurement_id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE
);
CREATE INDEX idx_incident_measurements_incident ON incident_measurements(incident_id);
INSERT INTO incident_measurements(measurement_id, incident_id)
SELECT origin_measurement_id, id FROM incidents;
