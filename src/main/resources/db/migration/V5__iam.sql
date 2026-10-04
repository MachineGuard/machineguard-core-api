CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    subscription_plan VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    acquisition_request_id UUID UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_organizations_plan CHECK (subscription_plan IN ('FREE', 'BASIC', 'PREMIUM')),
    CONSTRAINT ck_organizations_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'VIEWER')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_users_email_normalized CHECK (email = LOWER(email))
);

-- IAM writes the canonical lowercase value; the plain unique index is portable to PostgreSQL and H2.
CREATE UNIQUE INDEX uq_users_email_normalized ON users (email);
CREATE INDEX idx_users_organization_status ON users (organization_id, status);

CREATE TABLE authentication_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_auth_sessions_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX idx_auth_sessions_user ON authentication_sessions (user_id);

CREATE TABLE authentication_refresh_token_history (
    token_hash VARCHAR(64) PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES authentication_sessions (id) ON DELETE CASCADE,
    used_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_refresh_token_history_session ON authentication_refresh_token_history (session_id);

CREATE TABLE iam_bootstrap_state (
    bootstrap_key VARCHAR(40) PRIMARY KEY,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_iam_bootstrap_key CHECK (bootstrap_key = 'initial-admin')
);

CREATE TABLE initial_admin_invitations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_admin_invitation_expiry CHECK (expires_at > issued_at)
);
