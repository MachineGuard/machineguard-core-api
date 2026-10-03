# MachineGuard Core API · Traceability & Quality

Bounded Context **Traceability & Quality** de la RESTful API central de MachineGuard. Convierte las desviaciones ambientales en Excursions, vincula cada excursión con el incidente y la acción correctiva, registra No conformidades sobre lotes afectados y genera Traceability Reports sellados e inmutables para auditorías (HACCP, ISO 9001).

- Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL
- OpenAPI / Swagger (springdoc)
- DDD por capas: `domain`, `application`, `interfaces`, `infrastructure` en `com.machineguard.platform.traceability`

## Ejecución

Requiere JDK 21 o superior y una base PostgreSQL.

```bash
mvn spring-boot:run
```

Variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `TRACEABILITY_RETENTION_MONTHS` (60), `TRACEABILITY_PURGE_ENABLED` (false), `TRACEABILITY_MEASUREMENTS_TABLE` (measurements).

Swagger UI: `http://localhost:8080/swagger-ui.html`

Pruebas (H2 en modo PostgreSQL, no requieren base externa):

```bash
mvn test
```

## Endpoints (`/api/v1/traceability`)

Toda petición lleva las cabeceras `X-Organization-Id` y, en las que escriben, `X-User-Id` (ver _Identidad_). Todos los identificadores son UUID, igual que en IAM.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/excursions` | Historial filtrable por `monitoringZoneId`, `from`, `to`, `status` |
| GET | `/excursions/open` | Excursiones abiertas |
| GET | `/excursions/{id}` | Detalle con sus no conformidades |
| GET | `/excursions/{id}/measurement-history` | Measurement History de la excursión |
| POST | `/excursions/{id}/non-conformities` | Registra un lote afectado (queda `QUARANTINED`) |
| GET | `/excursions/{id}/non-conformities` | No conformidades de una excursión |
| PATCH | `/non-conformities/{id}/disposition` | `RELEASED` exige justificación; una resuelta no cambia |
| POST | `/reports` | Genera y sella un reporte (`monitoringZoneId`, `periodStart`, `periodEnd`) |
| GET | `/reports` | Lista los reportes (sin contenido) |
| GET | `/reports/{id}` | Reporte con contenido y `integrityVerified` |
| GET | `/reports/{id}/download` | Mismo reporte como adjunto JSON (para el ERP del cliente) |
| GET | `/measurement-history` | Historial de una zona en un periodo |

## Reglas de negocio implementadas

- Una desviación sin excursión abierta para la zona y variable inicia una nueva; con una abierta solo actualiza el valor pico (`open_key` único garantiza una sola abierta por zona y variable).
- La excursión se cierra con `SafeRangeRestored` o `IncidentResolved`; la duración se calcula entre inicio y cierre.
- Una excursión abierta no entra como evidencia cerrada en un reporte; se informa cuántas se excluyeron.
- Un reporte incluye Measurement History, excursiones cerradas y acciones correctivas, se sella con SHA-256 y es inmutable.
- Toda no conformidad pertenece a una excursión de la misma organización; liberar un lote exige justificación.
- Retention Period: `RetentionPolicyService` depura excursiones cerradas vencidas, salvo las incluidas en un reporte sellado. El job diario está desactivado por defecto (`TRACEABILITY_PURGE_ENABLED`).

## Integración con otros contextos

**Eventos que consume** (`application/integration/InboundEvents`), publicados con `ApplicationEventPublisher` dentro de la API central:

- `DeviationDetected` (Environmental Monitoring)
- `SafeRangeRestored` (Environmental Monitoring)
- `IncidentResolved` (Alert & Incident Management)

**Eventos que publica:** `ExcursionStarted`, `ExcursionEnded`, `TraceabilityReportGenerated`.

**Measurement History:** se lee de la tabla de Environmental Monitoring (`MeasurementHistoryProvider`, adaptador JDBC) con las columnas `organization_id`, `monitoring_zone_id`, `monitoring_point_id`, `environmental_variable`, `measured_value`, `recorded_at`.

**Identidad:** IAM entrega organización y usuario. Hasta integrar su filtro JWT se leen de `X-Organization-Id` y `X-User-Id`; al integrarlo basta con poblar esos valores desde el token validado.

**Base de datos:** la migración `V4_0__traceability_quality.sql` crea `excursions`, `non_conformities`, `traceability_reports` y `traceability_report_excursions`. Para la integración final se debe renumerar según el orden del resto de migraciones.
