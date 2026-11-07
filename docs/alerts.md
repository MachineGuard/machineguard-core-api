# Alertas e incidentes integrados

El paquete comun es `com.machineguard.platform.alertincident`; no se mantienen dos entidades Alert ni dos beans con el mismo nombre.

## Contrato HTTP

Los GET requieren JWT Bearer y ADMIN/VIEWER. Los POST de atencion requieren ADMIN. La organizacion y el autor salen del principal validado. Cabeceras X-Organization-Id/X-User-Id y campos de autor/organizacion en cuerpos JSON no sustituyen esa identidad.

| Metodo | Ruta | Parametros/cuerpo | Respuesta |
|---|---|---|---|
| GET | `/api/v1/alerts` | `monitoringZoneId`, `status` opcionales | 200 array, incluso vacio |
| GET | `/api/v1/alerts/{alertId}` | UUID | 200 o 404, tambien para tenant ajeno |
| GET | `/api/v1/incidents` | `monitoringZoneId`, `status`, `from`, `to`, `limit` opcionales | 200 array |
| GET | `/api/v1/incidents/{incidentId}` | UUID | 200 detalle o 404 |
| POST | `/api/v1/alerts/{alertId}/acknowledgements` | `{ "note": "Attending" }`, cuerpo opcional | 201; repetido/cerrado 409 |
| POST | `/api/v1/incidents/{incidentId}/corrective-actions` | `{ "description": "Repaired cooling", "notes": "Follow up" }` | 201; resuelto 409 |
| POST | `/api/v1/incidents/{incidentId}/resolve` | `{ "notes": "Conditions stable" }`, cuerpo opcional | 200; sin accion, fuera de rango o ya resuelto 409 |

JWT ausente/incorrecto/vencido: 401; rol insuficiente: 403; body/UUID/enum/periodo invalido: 400. El filtro JWT conserva el comportamiento de IAM: valida firma/expiracion sin consultar sesiones en cada request. Logout revoca renovacion; un access token ya emitido puede seguir vigente hasta expirar. `/auth/validate` comprueba el estado actual de sesion/usuario/organizacion. Esta integracion no cambia esa ventana de revocacion.

Alertas se ordenan por raisedAt descendente e id ascendente. El listado inicial de alertas no tiene paginacion. Incidentes admite `limit` por defecto 100, acotado entre 1 y 500; `from`/`to` filtran fecha de apertura. UUID y timestamps ISO 8601 se conservan; el modelo comun usa Instant.

### AlertResource

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "incidentId": "22222222-2222-2222-2222-222222222222",
  "monitoringZoneId": "33333333-3333-3333-3333-333333333333",
  "severity": "HIGH",
  "status": "PENDING",
  "message": "TEMPERATURE fuera de rango",
  "raisedAt": "2026-10-29T12:00:00Z",
  "acknowledgedAt": null,
  "escalatedAt": null
}
```

Severidad: LOW/MEDIUM/HIGH/CRITICAL. Estados de alerta: PENDING/ACKNOWLEDGED/ESCALATED/CLOSED. Incidente: OPEN/ACKNOWLEDGED/ESCALATED/RESOLVED. IncidentResource expone id, originMeasurementId, monitoringZoneId, environmentalVariable, status, openedAt, resolvedAt, resolvedBy y resolutionNotes. El detalle agrupa incident, alert, acknowledgement y correctiveActions. Una accion conserva performedBy y performedAt; el reconocimiento conserva acknowledgedBy, acknowledgedAt y note.

## Flujo de aplicacion y limites entre capas

1. Monitoring registra una medicion por variable y publica DeviationDetected solo si es vigente y esta fuera de rango.
2. IncidentOpeningEventHandler llama a RaiseAlertCommandService dentro de la misma transaccion. El handler no contiene reglas ni crea repositories.
3. El servicio recibe measurementId, organizationId, monitoringZoneId, environmentalVariable, measuredValue, minimumValue, maximumValue y detectedAt. Devuelve IncidentReference(incidentId, alertId, created). El primer evento abre el caso y su alerta; posteriores eventos recuperan sus identificadores.
4. El dominio valida transiciones y evidencias. Repositories del dominio tienen adapters JPA unicos; las proyecciones de consulta contienen la zona y aplican tenant en la consulta antes de devolver datos.
5. EnvironmentalConditionProvider es el port de aplicacion para consultar condicion ambiental. MonitoringConditionAdapter usa los repositories y el bloqueo del productor. El dominio de incidentes no conoce JWT, Spring Security ni repositories de infraestructura de Monitoring.
6. Al resolver, TraceabilityIncidentEventAdapter envia tambien openedAt. Traceability asocia todas las excursiones no vinculadas del periodo del incidente, no solo la ultima. El constructor legado de InboundEvents.IncidentResolved se conserva para consumidores/pruebas existentes.

### Transaccion, recuperacion y concurrencia

La apertura es sincrona: si falla un consumidor al guardar incidente/alerta, se revierte la medicion y sus efectos. El cliente puede reintentar la operacion fallida. La asociacion de cada medicion en incident_measurements se confirma dentro de esa misma transaccion.

La generacion serializa por zona con el mismo bloqueo que RegisterMeasurementCommandService y ConfigureThresholdCommandService. La resolucion adquiere primero zona y despues incidente; reconocimiento y acciones solo incidente. No se toma el bloqueo en orden inverso. La restriccion open_key de V6 conserva un unico incidente activo por organizacion/zona/variable. Las transiciones bloquean el incidente para evitar dos reconocimientos/resoluciones exitosos o una accion que ignore un cierre concurrente.

Volver al rango seguro no crea otro estado administrativo ni cierra el incidente. Si vuelve a desviarse antes de resolver, mantiene el incidente; despues de resolver, una nueva medicion desviada puede abrir otro. Se comprueba el umbral vigente y la ultima lectura por punto/variable, con al menos una lectura; no se infiere normalidad de la ausencia de datos. La politica de antiguedad maxima de lecturas no se amplia en este cambio.

Repetir una medicion ya absorbida en el servicio de generacion recupera el incidente original incluso si esta resuelto. Esto no cambia el contrato REST general de Monitoring: duplicar punto/variable/timestamp en POST /environmental-monitoring/measurements sigue dando 409. La idempotencia de transporte Edge requiere su contrato de ingesta y no debe confundirse con la correlacion interna.

## Evidencia, origen y resolucion

- Las mediciones nuevas indican `source`: `SENSOR` desde el gateway y `MANUAL` desde usuarios. Las anteriores quedan como `LEGACY_UNKNOWN`.
- Solo evidencia `SENSOR` vigente acredita disponibilidad y recuperacion. La ventana de vigencia es el mayor entre `CORE_FRESHNESS_MINIMUM_SECONDS` (30 s por defecto) y `CORE_FRESHNESS_INTERVAL_MULTIPLIER` (3 por defecto) veces el intervalo de muestreo del sensor.
- Para resolver se exige evidencia vigente de la variable del incidente en todos los puntos activos con sensores elegibles. Con varios sensores se toma la captura vigente mas reciente por punto; manuales y sensores inactivos no cuentan.
- El detalle devuelve `resolutionBlockReasons`, y el POST de resolucion vuelve a comprobarlo.
- Una medicion manual se conserva y puede alertar, pero no acredita recuperacion.
- `historical` indica el caracter actual del incidente; el mensaje original se conserva.
- Existe un incidente activo por organizacion, zona y variable (OPEN, ACKNOWLEDGED o ESCALATED). Crear o recuperar devuelve el mismo `incidentId`.
- `originMeasurementId` conserva la primera medicion. `incident_measurements` conserva todas las absorbidas, de modo que repetir una de ellas, incluso despues de resolver, no abre otro incidente.

## Migraciones

V2/V6 no se modifican. V7 renombra la columna de origen y crea incident_measurements, precargando el origen de incidentes existentes. Las referencias a mediciones son logicas, y la FK hacia el incidente mantiene integridad. La prueba IncidentMigrationIntegrationTest migra una base V6 con incidente/alerta existentes y comprueba que V7 conserva sus datos y su asociacion.

## Verificacion

```text
mvn clean verify
mvn -Dtest=AlertIncidentFlowIntegrationTest test
```

La suite incluye el flujo completo desde login e ingesta real por HTTP, recuperacion/recaida, varias excursiones de un mismo incidente, replay posterior al cierre, rollback de fallos, autorizacion por rol/tenant, identidad de autores, OpenAPI y concurrencia.

Los escenarios Gherkin de `src/test/resources/features/alertincident` cubren generacion y agrupacion, reconocimiento con identidad, resolucion condicionada a recuperacion y acceso de VIEWER. No acreditan notificaciones externas ni escalamiento automatico.

Para ejecutar estas pruebas sobre PostgreSQL real, siga [pruebas-y-ci.md](pruebas-y-ci.md#postgresql-real). El valor predeterminado sigue siendo H2 aislado.

## Alcance pendiente de otros componentes

Las rutas y servicios quedan integrados en Core. El cliente Web debe activar su repositorio HTTP y mapear severidad y nombre de zona para consumirlos; esta entrega no cambia pantallas. La credencial de gateway ya esta implementada (ver `gateways.md`). No se abren rutas de ingesta publicas ni se acepta una cabecera de dispositivo como identidad sin validarla.

Se conservan estados para escalamiento, pero no se implementa un job de escalamiento ni entrega Twilio. Estas capacidades no deben darse por terminadas a partir del CRUD y ciclo de vida de esta integracion.
# Resolution eligibility

Incident detail includes `withinSafeRange` (the latest monitored conditions) and
`resolutionAllowed` for the authenticated caller. Resolution is available only
to an ADMIN when the incident is active, a corrective action exists, and all
relevant latest readings are safe. These flags explain the UI state; the resolve
command rechecks conditions under locks and may still return 409 if they change.

