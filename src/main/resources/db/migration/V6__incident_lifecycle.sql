-- Bounded Context: Alert & Incident Management
-- Completa V2__alert_incident.sql con lo necesario para el ciclo de vida del incidente:
-- variable ambiental (para vincular la resolucion con la excursion de Traceability & Quality),
-- responsable y notas de resolucion, y el reconocimiento de la alerta (quien, cuando y nota).

ALTER TABLE incidents ADD COLUMN environmental_variable VARCHAR(20);
ALTER TABLE incidents ADD COLUMN resolved_by UUID;
ALTER TABLE incidents ADD COLUMN resolution_notes VARCHAR(1000);
-- Clave "org:zona:variable" mientras el incidente no esta resuelto (NULL al resolverse): garantiza a nivel de BD
-- un solo incidente sin resolver por zona y variable, aunque lleguen desviaciones concurrentes.
ALTER TABLE incidents ADD COLUMN open_key VARCHAR(100);

ALTER TABLE incidents ADD CONSTRAINT uq_incidents_open_key UNIQUE (open_key);
ALTER TABLE incidents ADD CONSTRAINT ck_incidents_variable
    CHECK (environmental_variable IS NULL OR environmental_variable IN ('TEMPERATURE', 'HUMIDITY'));

ALTER TABLE alerts ADD COLUMN acknowledged_by UUID;
ALTER TABLE alerts ADD COLUMN acknowledgement_note VARCHAR(500);
ALTER TABLE alerts ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE;

-- Filtros del listado de incidentes: organizacion, zona, estado y periodo.
CREATE INDEX idx_incidents_org_zone_opened ON incidents (organization_id, monitoring_zone_id, opened_at);
