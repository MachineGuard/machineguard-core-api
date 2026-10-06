-- Bounded Context: Alert & Incident Management
-- organization_id, monitoring_zone_id, deviation_id y performed_by son referencias logicas hacia
-- otros Bounded Contexts (no son Foreign Keys fisicas). Los identificadores son UUID, igual que en IAM.

CREATE TABLE incidents (
    id                 UUID PRIMARY KEY,
    deviation_id       UUID                     NOT NULL,
    organization_id    UUID                     NOT NULL,
    monitoring_zone_id UUID                     NOT NULL,
    status             VARCHAR(20)              NOT NULL,
    opened_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_incidents_deviation UNIQUE (deviation_id),
    CONSTRAINT ck_incidents_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'ESCALATED', 'RESOLVED')),
    CONSTRAINT ck_incidents_period CHECK (resolved_at IS NULL OR resolved_at >= opened_at)
);

CREATE INDEX idx_incidents_org_status ON incidents (organization_id, status);
CREATE INDEX idx_incidents_zone_opened ON incidents (monitoring_zone_id, opened_at);

CREATE TABLE alerts (
    id              UUID PRIMARY KEY,
    incident_id     UUID                     NOT NULL REFERENCES incidents (id),
    severity        VARCHAR(20)              NOT NULL,
    message         VARCHAR(500)             NOT NULL,
    status          VARCHAR(20)              NOT NULL,
    raised_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    escalated_at    TIMESTAMP WITH TIME ZONE,
    -- Un incidente tiene a lo sumo una alerta asociada.
    CONSTRAINT uq_alerts_incident UNIQUE (incident_id),
    CONSTRAINT ck_alerts_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_alerts_status CHECK (status IN ('PENDING', 'ACKNOWLEDGED', 'ESCALATED', 'CLOSED'))
);

CREATE INDEX idx_alerts_status_raised ON alerts (status, raised_at);

CREATE TABLE corrective_actions (
    id           UUID PRIMARY KEY,
    incident_id  UUID                     NOT NULL REFERENCES incidents (id),
    description  VARCHAR(1000)            NOT NULL,
    performed_by UUID                     NOT NULL,
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    notes        VARCHAR(1000)
);

CREATE INDEX idx_corrective_actions_incident ON corrective_actions (incident_id);
