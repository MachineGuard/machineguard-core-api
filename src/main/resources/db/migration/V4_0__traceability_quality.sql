-- Bounded Context: Traceability & Quality
-- organization_id, monitoring_zone_id, monitoring_point_id e incident_id son referencias logicas
-- hacia otros Bounded Contexts (no son Foreign Keys fisicas). Los identificadores son UUID, igual que en IAM.

CREATE TABLE excursions (
    id                     UUID PRIMARY KEY,
    organization_id        UUID                     NOT NULL,
    monitoring_zone_id     UUID                     NOT NULL,
    monitoring_point_id    UUID,
    environmental_variable VARCHAR(20)              NOT NULL,
    started_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at               TIMESTAMP WITH TIME ZONE,
    peak_value             NUMERIC(6, 2)            NOT NULL,
    threshold_value        NUMERIC(6, 2)            NOT NULL,
    severity               VARCHAR(20)              NOT NULL,
    status                 VARCHAR(20)              NOT NULL,
    incident_id            UUID,
    corrective_action      VARCHAR(1000),
    -- Clave "org:zona:variable" mientras la excursion esta ONGOING (NULL al cerrarse):
    -- garantiza a nivel de BD una sola excursion abierta por zona y variable.
    open_key               VARCHAR(100),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_excursions_open_key UNIQUE (open_key),
    CONSTRAINT ck_excursions_variable CHECK (environmental_variable IN ('TEMPERATURE', 'HUMIDITY')),
    CONSTRAINT ck_excursions_status CHECK (status IN ('ONGOING', 'CLOSED')),
    CONSTRAINT ck_excursions_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_excursions_period CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX idx_excursions_zone_started ON excursions (monitoring_zone_id, started_at);
CREATE INDEX idx_excursions_org_status ON excursions (organization_id, status);

CREATE TABLE non_conformities (
    id              UUID PRIMARY KEY,
    excursion_id    UUID                     NOT NULL REFERENCES excursions (id),
    organization_id UUID                     NOT NULL,
    batch_code      VARCHAR(60)              NOT NULL,
    product_name    VARCHAR(150),
    quantity        NUMERIC(10, 2),
    stored_at       TIMESTAMP WITH TIME ZONE,
    classification  VARCHAR(60)              NOT NULL,
    disposition     VARCHAR(20)              NOT NULL,
    justification   TEXT,
    registered_by   UUID                     NOT NULL,
    registered_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_non_conformities_disposition CHECK (disposition IN ('QUARANTINED', 'RELEASED', 'DISCARDED'))
);

CREATE INDEX idx_non_conformities_excursion ON non_conformities (excursion_id);

CREATE TABLE traceability_reports (
    id                 UUID PRIMARY KEY,
    organization_id    UUID                     NOT NULL,
    monitoring_zone_id UUID                     NOT NULL,
    period_start       TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end         TIMESTAMP WITH TIME ZONE NOT NULL,
    generated_by       UUID                     NOT NULL,
    generated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    excursion_count    INTEGER                  NOT NULL,
    measurement_count  INTEGER                  NOT NULL,
    status             VARCHAR(20)              NOT NULL,
    checksum           VARCHAR(64),
    sealed_at          TIMESTAMP WITH TIME ZONE,
    -- Snapshot JSON de la evidencia (excursiones, acciones correctivas y Measurement History).
    content            TEXT,
    CONSTRAINT ck_traceability_reports_status CHECK (status IN ('DRAFT', 'SEALED')),
    CONSTRAINT ck_traceability_reports_period CHECK (period_end >= period_start)
);

CREATE INDEX idx_traceability_reports_org ON traceability_reports (organization_id, generated_at);

CREATE TABLE traceability_report_excursions (
    traceability_report_id UUID                     NOT NULL REFERENCES traceability_reports (id),
    excursion_id           UUID                     NOT NULL REFERENCES excursions (id),
    included_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (traceability_report_id, excursion_id)
);
