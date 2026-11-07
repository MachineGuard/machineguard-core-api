# Traceability & Quality

Toda consulta filtra por el `organizationId` autenticado. Las rutas están bajo
`/api/v1/traceability`.

| Método | Ruta | Descripción | Acceso |
|---|---|---|---|
| GET | `/excursions` | Historial filtrable por `monitoringZoneId`, `from`, `to` y `status` | ADMIN/VIEWER |
| GET | `/excursions/open` | Excursiones abiertas | ADMIN/VIEWER |
| GET | `/excursions/{id}` | Detalle con no conformidades | ADMIN/VIEWER |
| GET | `/excursions/{id}/measurement-history` | Historial de mediciones de la excursión | ADMIN/VIEWER |
| POST | `/excursions/{id}/non-conformities` | Registra un lote afectado en cuarentena | ADMIN |
| GET | `/excursions/{id}/non-conformities` | No conformidades de una excursión | ADMIN/VIEWER |
| PATCH | `/non-conformities/{id}/disposition` | Cambia el destino del lote; liberar exige justificación | ADMIN |
| GET | `/late-deviations` | Desviaciones tardías de una zona (`monitoringZoneId`, `limit` 1-500, por defecto 100) | ADMIN/VIEWER |
| POST | `/reports` | Genera y sella un reporte por zona y periodo | ADMIN |
| GET | `/reports` | Lista reportes sin contenido | ADMIN/VIEWER |
| GET | `/reports/{id}` | Lee un reporte y comprueba su integridad | ADMIN/VIEWER |
| GET | `/reports/{id}/download` | Descarga el reporte JSON | ADMIN/VIEWER |
| GET | `/measurement-history` | Historial de una zona y periodo | ADMIN/VIEWER |

## Reglas de negocio

- Una desviación sin excursión abierta para la zona y variable inicia una nueva. Con una
  abierta, solo actualiza el valor pico. La clave `open_key` impide duplicar excursiones abiertas.
- La excursión se cierra con `SafeRangeRestored` o `IncidentResolved`. La duración se calcula
  entre inicio y cierre.
- Una excursión abierta no entra como evidencia cerrada en un reporte; el reporte indica cuántas se excluyeron.
- Un reporte incluye Measurement History, las excursiones cerradas y las acciones correctivas.
  Se sella con SHA-256 y es inmutable. Los reportes sellados no se modifican.
- Toda no conformidad pertenece a una excursión de la misma organización. Liberar un lote exige justificación.
- `RetentionPolicyService` depura excursiones cerradas vencidas, salvo las incluidas en un
  reporte sellado. El job diario está desactivado por defecto (`TRACEABILITY_PURGE_ENABLED=false`).

## Integración con otros contextos

Traceability recibe como eventos Spring, dentro del proceso de la API, los eventos
`DeviationDetected` y `SafeRangeRestored` de Environmental Monitoring, y `IncidentResolved`
de Alert & Incident. Publica `ExcursionStarted`, `ExcursionEnded` y `TraceabilityReportGenerated`.

Measurement History se lee mediante `MeasurementHistoryProvider` y su adaptador JDBC. La tabla
configurable (`TRACEABILITY_MEASUREMENTS_TABLE`, por defecto `measurements`) debe tener las columnas
`organization_id`, `monitoring_zone_id`, `monitoring_point_id`, `environmental_variable`,
`measured_value` y `recorded_at`. Esa tabla pertenece a Environmental Monitoring. Un `503`
indica que la tabla configurada no está disponible.

Las migraciones que usa Traceability están en [base-de-datos.md](base-de-datos.md).

## Desviaciones tardías

`GET /api/v1/traceability/late-deviations?monitoringZoneId=<uuid>&limit=100` lista la evidencia
de desviaciones tardías. Ver [gateway-ingestion.md](gateway-ingestion.md#capturas-tardías-y-límites-de-tiempo).
