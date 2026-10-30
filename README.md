# MachineGuard Core API

Para probar la integración completa en esta PC, consultar el
[entorno local de testing para Windows](docs/local-testing.md): inicio y parada
de Web/Core/PostgreSQL aislados, cuentas ADMIN/VIEWER y conservación de datos.

REST API central de MachineGuard con los bounded contexts **IAM**, **Environmental Monitoring**, **Alert & Incident Management** y **Traceability & Quality**, desarrollada con Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway y PostgreSQL. Los identificadores son UUID. La documentación OpenAPI y Swagger UI se sirven desde la propia API.

## Inicio local con Docker Compose

Requiere Docker Desktop con el daemon iniciado y Docker Compose v2. Crea la configuración local y reemplaza los valores de ejemplo antes de iniciar los servicios:

```powershell
Copy-Item .env.example .env
```

Configura `MACHINEGUARD_DB_PASSWORD` e `IAM_JWT_SECRET`. La clave JWT requiere al menos 32 bytes en UTF-8. El archivo `.env` está excluido de Git y debe mantenerse local. Para iniciar:

```bash
docker compose up --build
```

La API espera a que PostgreSQL esté saludable y luego ejecuta las migraciones Flyway. La API queda en `http://localhost:8080`; PostgreSQL persiste en el volumen `machineguard-postgres`. Para detener los procesos conserva la base con `docker compose down`; `docker compose down -v` también elimina los datos locales.

### Administrador inicial local

Para crear la organización `FREE` y su administrador una sola vez, establece estos valores en `.env` antes del primer inicio:

```dotenv
IAM_BOOTSTRAP_ENABLED=true
IAM_BOOTSTRAP_ORGANIZATION_NAME=Mi organización
IAM_BOOTSTRAP_ADMIN_NAME=Nombre del administrador
IAM_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
IAM_BOOTSTRAP_ADMIN_PASSWORD=una-frase-segura-de-al-menos-12-caracteres
```

Tras crear el administrador, IAM guarda un marcador persistente para que reiniciar la API no vuelva a crear el usuario. Si la API se inició antes con el bootstrap desactivado, se puede activar después mientras la base no tenga organizaciones ni usuarios. Si ya existen organizaciones o usuarios, el bootstrap se detiene y no modifica esos registros. La API nunca registra la contraseña.

### Ejecución sin Docker

Se requiere Java 21 y Maven. Configura `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e `IAM_JWT_SECRET` en el entorno y ejecuta:

```bash
mvn spring-boot:run
```

## Datos de demostración

`scripts/demo_data.py` prepara una organización para una demostración usando solo la API REST (Python 3.8+, sin dependencias). Lee `MG_API_URL` (por defecto `http://localhost:8080`), `MG_ADMIN_EMAIL` y `MG_ADMIN_PASSWORD`; con `MG_VIEWER_EMAIL` y `MG_VIEWER_PASSWORD` crea además un usuario `VIEWER`. Las credenciales nunca se escriben en el repositorio.

| Comando | Qué hace |
|---|---|
| `python scripts/demo_data.py seed` | Crea tres zonas (dos completas y una a medio configurar), sus puntos, sensores y Safe Ranges, y carga 24 h de mediciones con una excursión ya cerrada. Se puede repetir: no duplica nada. |
| `python scripts/demo_data.py live` | Publica una medición por sensor cada minuto, como si los nodos estuvieran instalados. Pensado para dejarlo corriendo en el servidor. |
| `python scripts/demo_data.py excursion` | Saca la cámara fría de su Safe Range durante unos minutos y la devuelve, para mostrar la apertura y el cierre de una excursión. |

Mientras `live` está activo, una medición fuera de rango (enviada con `excursion` o desde Swagger) se respeta durante cinco minutos antes de que las lecturas normales la restablezcan.

## IAM

El acceso usa JWT HMAC-SHA256 con `userId` en `sub`, `organizationId`, `role` (`ADMIN`/`VIEWER`) y `sid`; la duración por defecto es 15 minutos. Las claves y contraseñas se leen del entorno. Cada sesión conserva el hash SHA-256 del refresh token. El refresh rota el token y conserva los hashes consumidos para rechazar su reutilización y revocar esa sesión. Al cambiar contraseña o desactivar a un usuario, se revocan sus sesiones renovables.

Los correos se normalizan a minúsculas y son únicos globalmente. Solo un administrador activo de la organización puede registrar y administrar usuarios. El último administrador activo no puede degradarse ni desactivarse. Los cambios administrativos bloquean la fila de la organización durante la comprobación del último administrador para evitar carreras entre transacciones. Usuarios u organizaciones inactivos/suspendidos no pueden iniciar sesión ni renovar una sesión. Las contraseñas requieren al menos 12 caracteres y como máximo 72 bytes UTF-8 para coincidir con el límite de BCrypt.

| Método | Ruta | Descripción | Acceso |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Autentica correo y contraseña; devuelve access y refresh token | Público |
| POST | `/api/v1/auth/refresh` | Rota el refresh token | Público con refresh token |
| POST | `/api/v1/auth/logout` | Revoca la sesión asociada al refresh token | Bearer |
| POST | `/api/v1/auth/password-reset` | Verifica la contraseña actual, cambia la clave y revoca todas las sesiones renovables | Bearer |
| POST | `/api/v1/auth/validate` | Verifica JWT, sesión, usuario y organización activos | Bearer |
| POST | `/api/v1/auth/invitations/accept` | Canjea una invitación de administrador de un solo uso por una contraseña | Público con invitación |
| GET | `/api/v1/users/me` | Consulta el perfil autenticado | Bearer |
| POST | `/api/v1/organizations/{organizationId}/users` | Registra un usuario en el tenant autenticado | Bearer + ADMIN |
| PATCH | `/api/v1/users/{userId}/role` | Cambia `ADMIN`/`VIEWER` en el mismo tenant | Bearer + ADMIN |
| PATCH | `/api/v1/users/{userId}/status` | Activa/desactiva un usuario del mismo tenant | Bearer + ADMIN |
| GET | `/api/v1/organizations/{organizationId}` | Consulta la organización del tenant autenticado | Bearer |

No hay un endpoint público para crear organizaciones. IAM recibe `PilotRequestSubmitted(requestId, companyName, contactName, contactEmail)` como evento Spring local; la conexión con Customer Acquisition aún está pendiente. IAM crea la organización y un administrador inactivo, y publica `InitialAdminInvitationIssued` con un token de un solo uso. En producción, el servicio de notificaciones debe entregar al contacto el enlace HTTPS de invitación. El bootstrap local crea directamente un administrador activo.

Los endpoints IAM protegidos y todos los de Traceability requieren `Authorization: Bearer <accessToken>`. Traceability obtiene `userId`, `organizationId` y rol de los claims JWT firmados; ignora las cabeceras `X-Organization-Id` y `X-User-Id`. Solo `ADMIN` puede crear reportes o registrar/cambiar disposiciones de no conformidades; `VIEWER` solo puede consultar. El filtro valida la firma y expiración del token en cada solicitud, pero no consulta la base de datos. Un access token activo puede seguir aceptándose hasta expirar; `/api/v1/auth/validate` también comprueba el estado actual de la sesión, el usuario y la organización.

## Environmental Monitoring

Implementado en `com.machineguard.platform.environmentalmonitoring` con las mismas capas de Traceability: entidades de dominio JPA, repositories de dominio, commands/queries con servicios `handle`, eventos Spring, Resources y Assemblers. Los cinco contratos de repositories permanecen en el dominio; sus adapters explícitos en `infrastructure/persistence/adapter` delegan en repositories JPA técnicos. Los servicios no construyen consultas JPA. Reutiliza V1 sin modificarla. Cada medición corresponde a **una variable**, con `BigDecimal` y `OffsetDateTime`.

Todos los endpoints bajo `/api/v1/environmental-monitoring` requieren JWT. La organización proviene del token firmado; las cabeceras `X-Organization-Id` y `X-User-Id` no determinan el tenant.

| Método | Ruta relativa | Descripción | Acceso |
|---|---|---|---|
| POST | `/measurements` | Registra una medición; devuelve 201, Resource y Location | ADMIN |
| GET | `/measurements/{id}` | Consulta una medición del tenant | ADMIN/VIEWER |
| GET | `/measurements/latest` | Última lectura por punto y variable; zona opcional | ADMIN/VIEWER |
| GET | `/measurements/history` | Historial por zona y periodo, filtros y paginación | ADMIN/VIEWER |
| GET | `/zones` | Zonas con puntos, sensores, umbrales y lecturas actuales | ADMIN/VIEWER |
| GET | `/zones/{id}` | Proyección de una zona del tenant | ADMIN/VIEWER |
| POST | `/zones` | Registra una Monitoring Zone; devuelve 201, Resource y Location | ADMIN |
| POST | `/zones/{zoneId}/points` | Registra un Monitoring Point en la zona | ADMIN |
| POST | `/zones/{zoneId}/points/{pointId}/sensors` | Registra un Sensor Node; `deviceCode` es único | ADMIN |
| PUT | `/zones/{zoneId}/thresholds/{environmentalVariable}` | Configura o reemplaza el Safe Range (`TEMPERATURE` o `HUMIDITY`) | ADMIN |

Una organización nueva se prepara en este orden: zona, punto, sensor (opcional) y un umbral por variable. `POST /measurements` responde `409` mientras la variable no tenga umbral configurado. Cada configuración de umbral publica `ThresholdConfigured`.

`DeviationDetected` se publica por cada lectura **vigente** fuera de rango, conforme al contrato de Traceability que actualiza el pico de una única excursión abierta. `SafeRangeRestored` se publica una sola vez al volver al rango, siempre que ningún otro punto de esa zona y variable siga desviado. El command service publica a través del port `EnvironmentalMonitoringEventPublisher`; su implementación Spring y el puente de compatibilidad con los records `InboundEvents` existentes están en `infrastructure/integration`. Los detalles, contratos, supuestos, árbol de archivos y cobertura están en [Environmental Monitoring](docs/environmental-monitoring.md).

## Traceability & Quality

Toda consulta filtra por el `organizationId` autenticado. Los endpoints principales bajo `/api/v1/traceability` son:

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/excursions` | Historial filtrable por `monitoringZoneId`, `from`, `to`, `status` |
| GET | `/excursions/open` | Excursiones abiertas |
| GET | `/excursions/{id}` | Detalle con no conformidades |
| GET | `/excursions/{id}/measurement-history` | Historial de mediciones de la excursión |
| POST | `/excursions/{id}/non-conformities` | Registra un lote afectado en cuarentena |
| GET | `/excursions/{id}/non-conformities` | No conformidades de una excursión |
| PATCH | `/non-conformities/{id}/disposition` | Cambia el destino del lote; liberar exige justificación |
| POST | `/reports` | Genera y sella un reporte por zona y periodo |
| GET | `/reports` | Lista reportes sin contenido |
| GET | `/reports/{id}` | Lee un reporte y comprueba su integridad |
| GET | `/reports/{id}/download` | Descarga el reporte JSON |
| GET | `/measurement-history` | Historial de una zona y periodo |

### Reglas de negocio

- Una desviación sin excursión abierta para la zona y variable inicia una nueva; con una abierta solo actualiza el valor pico. La clave `open_key` impide duplicar excursiones abiertas.
- La excursión se cierra con `SafeRangeRestored` o `IncidentResolved`; la duración se calcula entre inicio y cierre.
- Una excursión abierta no se incorpora como evidencia cerrada en un reporte; se informa cuántas se excluyeron.
- Un reporte incluye Measurement History, excursiones cerradas y acciones correctivas, se sella con SHA-256 y es inmutable.
- Toda no conformidad pertenece a una excursión de la misma organización; liberar un lote exige justificación.
- `RetentionPolicyService` depura excursiones cerradas vencidas, salvo las incluidas en un reporte sellado. El job diario está desactivado por defecto (`TRACEABILITY_PURGE_ENABLED=false`).

### Integración con otros contextos

Los eventos `DeviationDetected` y `SafeRangeRestored` de Environmental Monitoring e `IncidentResolved` de Alert & Incident Management se reciben como eventos Spring dentro del proceso de la API. Traceability publica `ExcursionStarted`, `ExcursionEnded` y `TraceabilityReportGenerated`.

Measurement History se lee mediante `MeasurementHistoryProvider` y su adaptador JDBC de la tabla configurable `measurements`, con las columnas `organization_id`, `monitoring_zone_id`, `monitoring_point_id`, `environmental_variable`, `measured_value` y `recorded_at`. Esa tabla pertenece a Environmental Monitoring y está creada por V1. Environmental Monitoring ya registra mediciones en ella; el adaptador JDBC existente las consume sin cambios en Traceability. Un `503` indica que la tabla configurada no está disponible.

Las migraciones Flyway se ordenan así: `V1__environmental_monitoring.sql` (zonas, puntos, nodos sensores, umbrales y mediciones), `V2__alert_incident.sql` (incidentes, alertas y acciones correctivas), `V4__traceability_quality.sql` (`excursions`, `non_conformities`, `traceability_reports` y `traceability_report_excursions`), `V5__iam.sql` (tablas IAM) `V6__incident_lifecycle.sql` (ciclo de vida del incidente) y `V7__incident_integration.sql` (origen de mediciones e integracion); `V3` queda reservada. Traceability usa la identidad autenticada por JWT y no acepta identificadores de usuario u organización enviados por el cliente.

## Alert & Incident Management

Las entregas de alertas e incidentes se integran en un unico contexto, `com.machineguard.platform.alertincident`, con una sola entidad `Alert` y un solo repository/adapter. Se mantienen las consultas y filtros de alertas junto con el ciclo de vida de incidentes. Las ramas originales permanecen en el historial mediante merges.

| Metodo | Ruta | Funcion | Acceso |
|---|---|---|---|
| GET | `/api/v1/alerts` | Alertas del tenant; filtros `monitoringZoneId`, `status` | ADMIN/VIEWER |
| GET | `/api/v1/alerts/{alertId}` | Detalle de alerta, incluida su zona | ADMIN/VIEWER |
| GET | `/api/v1/incidents` | Incidentes por zona, estado y periodo; `limit` entre 1 y 500 | ADMIN/VIEWER |
| GET | `/api/v1/incidents/{incidentId}` | Incidente, alerta, reconocimiento y acciones | ADMIN/VIEWER |
| POST | `/api/v1/alerts/{alertId}/acknowledgements` | Reconoce con `note` opcional | ADMIN |
| POST | `/api/v1/incidents/{incidentId}/corrective-actions` | Registra `description` y `notes` opcionales | ADMIN |
| POST | `/api/v1/incidents/{incidentId}/resolve` | Resuelve con `notes` opcionales | ADMIN |

Organizacion y autores proceden de `IamPrincipal`, nunca de cabeceras o campos de identidad suministrados por el consumidor. Recursos ajenos se devuelven como 404. JWT ausente/invalido/vencido produce 401; rol insuficiente, 403. Cada reconocimiento inicial y accion nueva devuelve 201; reconocimiento repetido, cambios sobre incidentes resueltos y resolucion sin condiciones producen 409.

### Generacion y cierre

- El unico handler de `DeviationDetected` coordina un servicio de aplicacion sincrono. Medicion, incidente, alerta y efectos de trazabilidad se confirman o revierten juntos. No se oculta un fallo de generacion despues de aceptar la medicion.
- Existe un incidente activo por organizacion, zona y variable, incluyendo estados OPEN, ACKNOWLEDGED y ESCALATED. Se devuelve `incidentId` tanto al crear como al recuperar.
- `originMeasurementId` conserva la primera medicion. `incident_measurements` conserva todas las mediciones absorbidas para que repetir una de ellas, incluso despues de resolver, no abra otro incidente.
- Volver al rango cierra la excursion fisica, pero no resuelve el incidente. Resolver exige al menos una accion correctiva y que las ultimas mediciones conocidas de todos los puntos para esa variable esten dentro del umbral vigente. Sin mediciones o sin umbral no se permite resolver.
- Generacion y resolucion usan el mismo bloqueo de zona que la ingesta y configuracion de umbrales. Reconocimiento, acciones y resolucion bloquean el incidente para serializar sus transiciones y conservar autor/estado.
- La resolucion cierra la alerta y vincula las excursiones del periodo del incidente con sus acciones correctivas, incluyendo recuperaciones y recaidas antes del cierre administrativo.

V1, V2, V4, V5 y V6 permanecen intactas. `V7__incident_integration.sql` renombra `deviation_id` a `origin_measurement_id` y crea las asociaciones de mediciones, conservando los datos existentes. Flyway aplica V7 al actualizar una base que ya tenga V6; no hace falta recrearla por esta integracion.

Los contratos, limites y comandos de verificacion estan en [Alertas e incidentes](docs/alerts.md).

## OpenAPI, configuración y pruebas

### Gateways y transporte Edge

Los gateways se aprovisionan mediante un ADMIN activo y reciben una credencial opaca limitada a los sensores de su organización. La máquina usa `X-Device-Credential`, con permisos exclusivos de ingesta: `POST /api/v1/measurements` y `/api/v1/measurements/batch`. Las lecturas conservan su instante de captura; los reintentos idénticos se confirman sin duplicar datos. El JWT de usuario no sustituye esta identidad de gateway.

Contratos: [Credenciales de gateway](docs/gateways.md) y [Ingesta Edge-Core](docs/gateway-ingestion.md).

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Documento OpenAPI: `http://localhost:8080/v3/api-docs`
- Variables IAM: `IAM_JWT_SECRET`, `IAM_ACCESS_TOKEN_TTL_SECONDS` (de 60 a 3600 segundos), `IAM_REFRESH_TOKEN_TTL_DAYS` (por defecto 30) y `IAM_BOOTSTRAP_*`.
- Variables de base de datos: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Compose permite configurar los puertos expuestos mediante `API_PORT` (8080 por defecto) y `POSTGRES_PORT` (5432 por defecto).
- Traceability: `TRACEABILITY_RETENTION_MONTHS` (60), `TRACEABILITY_PURGE_ENABLED` (false), `TRACEABILITY_MEASUREMENTS_TABLE` (`measurements`).
- Pruebas de integración con H2: `mvn test`.

### Pruebas BDD (Cucumber)

Los criterios de aceptación de las historias del Sprint 1 (TS06, TS07, TS08, US02, US10, US11 y US12) están escritos en Gherkin, en español, en `src/test/resources/features`. Los pasos están en `src/test/java/com/machineguard/platform/acceptance` y se ejecutan contra la API con MockMvc sobre una base H2 propia.

- Toda la batería: `mvn test`. Solo BDD: `mvn test -Dtest=CucumberAcceptanceTest`.
- Reporte HTML: `target/cucumber-reports/cucumber.html`.
- GitHub Actions (`.github/workflows/ci.yml`) ejecuta las pruebas en cada push y pull request a `develop` y `main`, y publica el reporte como artefacto.
