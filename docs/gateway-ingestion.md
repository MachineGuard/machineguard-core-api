# Edge-to-Core ingestion contract

The gateway authenticates with X-Device-Credential issued by the IAM gateway API. It has GATEWAY_INGEST authority, not ADMIN/VIEWER privileges. Its organization and authorized Sensor Node IDs come from that validated identity, never from submitted tenant/point fields.

## Requests

- POST `/api/v1/measurements`: one calibrated reading.
- POST `/api/v1/measurements/batch`: `{ "measurements": [ ... ] }`, 1-500 readings per request.

```json
{
  "deviceCode": "MG-01",
  "temperature": 11.5,
  "humidity": 50.0,
  "capturedAt": "2026-10-30T12:00:00.123456Z"
}
```

Each reading creates or confirms TEMPERATURE and HUMIDITY measurements separately. Values must fit NUMERIC(6,2). Captures normalize to UTC microseconds and cannot exceed server time by more than five minutes. Historical samples preserve their timestamp and follow Monitoring's existing backfill policy. Active zone/point and a non-inactive sensor are required. A device unknown, foreign, inactive or outside the gateway's explicit sensor scope returns 404.

## Confirmation and atomicity

```json
{ "readingsAccepted": 1, "measurementsCreated": 2, "measurementsReused": 0 }
```

New measurements produce 201; a request containing only identical accepted data produces 200. The receipt confirms every submitted reading; created + reused equals twice readingsAccepted. A sensor/point/variable/capture already recorded with the same value and sensor is reused without new domain events. Different data at that identity is 409 and never overwrites evidence. A lost HTTP reply can therefore be retried safely. The original human Monitoring endpoint retains its prior duplicate-409 contract.

Authorization/mapping are resolved before recording; zones are locked in stable UUID order, and readings are processed chronologically. Both variables and all readings of one request share the existing transaction with alerts/incidents/traceability. Failure in a later variable or reading rolls the whole request back. The same zone lock serializes simultaneous retries and prevents deadlocks from batches submitted in opposite zone order.

No credential, invalid/expired/revoked credential, or mixed machine/Bearer headers returns 401. A human token on these machine routes returns 403. Body/size/future-date errors return 400. An existing legitimate measurement conflict or missing threshold returns 409. No partial-success receipt is emitted.

The Edge client splits larger local selections into requests of at most 500. If a later chunk fails, it keeps the whole selection pending and confirms already accepted chunks idempotently on retry. It requires both a 2xx status and the matching JSON receipt, and never follows redirects with the machine credential.

Provision sensors in Core first, then issue a gateway scoped to those sensor UUIDs. Configure the same physical deviceCode in Edge and set MACHINEGUARD_API_URL and private EDGE_DEVICE_CREDENTIAL. EDGE_GATEWAY_ID is a local buffer label and is not accepted as authentication.

Tests: GatewayIngestionApiIntegrationTest exercises both variables, lost confirmation, conflicting retries, batches, rollback, time precision, authorization, credential lifecycle and concurrency. The application service uses GatewayDeviceResolver and existing RegisterMeasurementCommandService; it does not interpret JWTs or directly query infrastructure repositories.
