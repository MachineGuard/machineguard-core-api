-- Machine identities remain separate from human IAM users and roles.
CREATE TABLE gateway_credentials (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    credential_hash VARCHAR(64) NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_gateway_credentials_org ON gateway_credentials(organization_id, created_at);
CREATE TABLE gateway_sensor_nodes (
    gateway_id UUID NOT NULL REFERENCES gateway_credentials(id) ON DELETE CASCADE,
    sensor_node_id UUID NOT NULL,
    PRIMARY KEY (gateway_id, sensor_node_id)
);
