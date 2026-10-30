# Gateway identities

Gateways are IAM machine identities, separate from ADMIN/VIEWER users. A gateway belongs to one organization and an explicit set of 1-100 registered Sensor Nodes. Its only authority is GATEWAY_INGEST for POST `/api/v1/measurements` and `/api/v1/measurements/batch`.

An active ADMIN provisions, rotates and revokes credentials using the normal Bearer JWT. The service also checks the current persisted user role/status and organization status, so a stale ADMIN claim cannot provision credentials after demotion or suspension.

| Method | Route | Purpose |
|---|---|---|
| POST | `/api/v1/gateways` | Register name, sensorNodeIds and optional credentialTtlDays |
| GET | `/api/v1/gateways` | List metadata in the authenticated organization |
| GET | `/api/v1/gateways/{id}` | Read metadata; foreign/unknown identity returns 404 |
| POST | `/api/v1/gateways/{id}/credentials` | Rotate, optionally changing credentialTtlDays |
| POST | `/api/v1/gateways/{id}/revoke` | Revoke idempotently |

Issuance/rotation returns `{ gateway: {...}, credential: "..." }` with Cache-Control: no-store. The opaque credential is supplied once and only its SHA-256 hash is persisted. Copy it to the gateway's private EDGE_DEVICE_CREDENTIAL environment variable; never commit it. Metadata GETs do not expose credentials or hashes.

TTL defaults to 30 days and accepts 1-90 days. Rotation replaces the previous hash immediately for subsequent authentications. Revocation cannot be undone by rotation: provision a new identity. The authorized sensor set is immutable for that identity. Every machine authentication checks expiry, revocation and organization activity. Already authenticated in-flight requests may finish; revocation denies subsequent authentications.

The gateway sends X-Device-Credential, without Authorization. Supplying both credentials is rejected with 401. The machine cannot use protected human APIs; those require ADMIN or VIEWER. A human JWT alone cannot use the machine ingestion routes. Existing user JWT/refresh semantics remain unchanged.

Sources follow the existing IAM boundaries: Gateway domain model and repository port; credential application service and sensor-ownership port; JPA persistence and Monitoring ownership adapter in infrastructure; HTTP resources and filters at the boundary. No machine ADMIN user or additional human role is introduced.

Tests: `mvn -Dtest=GatewayDomainTest,GatewayCredentialApiIntegrationTest,IamApiIntegrationTest test`.
