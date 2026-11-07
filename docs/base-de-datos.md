# Base de datos y migraciones

Flyway aplica las migraciones de `src/main/resources/db/migration` al iniciar la API.
Hibernate no modifica el esquema (`ddl-auto: none`). No cambie una migración ya aplicada:
los cambios van en una versión nueva.

| Versión | Contenido |
|---|---|
| V1 `environmental_monitoring` | Zonas, puntos, nodos sensores, umbrales y mediciones |
| V2 `alert_incident` | Incidentes, alertas y acciones correctivas |
| V3 | Reservada; no existe archivo |
| V4 `traceability_quality` | Excursiones, no conformidades y reportes sellados |
| V5 `iam` | Organizaciones, usuarios, sesiones, historial de refresh tokens, estado del bootstrap e invitaciones |
| V6 `incident_lifecycle` | Ciclo de vida de incidentes y alertas (estados y clave `open_key`) |
| V7 `incident_integration` | Renombra `deviation_id` a `origin_measurement_id` y crea `incident_measurements` |
| V8 `gateway_credentials` | Credenciales de gateway y sensores autorizados por gateway |
| V9 `late_measurement_evidence` | Tabla `late_deviations` y marca de incidentes históricos (`historical`, `live_started_at`) |
| V10 `measurement_source` | Origen de cada medición: `SENSOR`, `MANUAL` o `LEGACY_UNKNOWN` |
| V11 `pilot_requests` | Solicitudes de piloto de la Landing |

Notas:

- V7 se aplica sobre una base que ya tiene V6; no hace falta recrear la base.
- La tabla `measurements` pertenece a Environmental Monitoring (V1). Traceability la lee
  mediante JDBC; el nombre se configura con `TRACEABILITY_MEASUREMENTS_TABLE`.
- Las referencias entre módulos se guardan como UUID. Las relaciones internas tienen claves foráneas.
