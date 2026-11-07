-- Customer acquisition: pilot requests from the Landing, approved manually by an operator.
CREATE TABLE pilot_requests (
    id UUID PRIMARY KEY,
    company_name VARCHAR(150) NOT NULL,
    contact_name VARCHAR(150) NOT NULL,
    contact_email VARCHAR(150) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    industry VARCHAR(20) NOT NULL,
    estimated_monthly_loss NUMERIC(14, 2),
    status VARCHAR(20) NOT NULL,
    -- Set only while PENDING: the unique index allows one pending request per normalized email.
    -- NULL values are distinct in PostgreSQL and H2, so decided requests never collide.
    pending_contact_email VARCHAR(150) UNIQUE,
    organization_id UUID REFERENCES organizations (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_pilot_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_pilot_requests_industry CHECK (industry IN ('FOOD', 'PHARMA', 'CHEMICAL', 'VETERINARY', 'OTHER')),
    CONSTRAINT ck_pilot_requests_email_normalized CHECK (contact_email = LOWER(contact_email)),
    CONSTRAINT ck_pilot_requests_loss CHECK (estimated_monthly_loss IS NULL OR estimated_monthly_loss >= 0),
    CONSTRAINT ck_pilot_requests_pending_email CHECK (
        (status = 'PENDING' AND pending_contact_email = contact_email)
        OR (status <> 'PENDING' AND pending_contact_email IS NULL)),
    CONSTRAINT ck_pilot_requests_decision CHECK (
        (status = 'PENDING' AND decided_at IS NULL AND organization_id IS NULL)
        OR (status = 'REJECTED' AND decided_at IS NOT NULL AND organization_id IS NULL)
        OR (status = 'APPROVED' AND decided_at IS NOT NULL AND organization_id IS NOT NULL))
);

CREATE INDEX idx_pilot_requests_status_created ON pilot_requests (status, created_at);
