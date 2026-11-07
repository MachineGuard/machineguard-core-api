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

## Códigos de error

Cada rechazo responde un `ProblemDetail` con la propiedad `code`. Los códigos son el contrato de máquina; el texto puede cambiar.

| Código | HTTP | Significado |
|---|---|---|
| `READING_INVALID` | 400 | Formato o valores inválidos en la medición o el lote |
| `READING_TIME_INVALID` | 400 | Captura fuera de los límites de tiempo |
| `NODE_NOT_AUTHORIZED` | 404 | Dispositivo desconocido, ajeno o fuera del alcance del gateway |
| `NODE_INACTIVE` | 409 | Zona, punto o sensor inactivo |
| `THRESHOLD_NOT_CONFIGURED` | 409 | Falta el umbral de la variable |
| `READING_CONFLICT` | 409 | Ya existe otra lectura distinta para ese punto, variable e instante |
| `INGESTION_REJECTED` | 409 | Otro rechazo de regla de negocio |
| `GATEWAY_CREDENTIAL_REJECTED` | 401 | Credencial de gateway ausente, inválida, vencida o revocada |
| `GATEWAY_INGEST_FORBIDDEN` | 403 | Token humano usado en una ruta de máquina |

Los estados HTTP y los recibos de éxito no cambian, y cada lote sigue siendo atómico. El cliente Edge conserva las lecturas pendientes ante códigos que no reconoce; ese comportamiento pertenece al repositorio Edge.

## Capturas tardías y límites de tiempo

Las capturas requieren zona horaria. Se rechazan las anteriores a `CORE_MIN_RECORDED_AT` (por defecto 01/01/2026 UTC), las de más de `CORE_MAX_READING_AGE_DAYS` (30 días) y las que se adelantan más de `CORE_MAX_FUTURE_SECONDS` (60 s). Los valores están en [configuracion.md](configuracion.md).

Las desviaciones tardías generan evidencia y se agrupan en un incidente sin cambiar el estado ambiental actual. `historical` indica que todas sus mediciones son tardías; si recibe una desviación vigente, pasa a ser un caso actual. Cerrar un caso histórico no vincula ni cierra excursiones. La evidencia se consulta con `GET /api/v1/traceability/late-deviations?monitoringZoneId=<uuid>&limit=100` (JWT ADMIN o VIEWER, `limit` máximo 500). Los reportes sellados no se modifican.
