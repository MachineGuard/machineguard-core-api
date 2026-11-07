# Environmental Monitoring

## Arquitectura y persistencia

La inspección de Traceability encontró entidades JPA en `domain/model`, interfaces Spring Data en `domain/repositories` implementadas automáticamente por Spring y un port `application/ports/MeasurementHistoryProvider` implementado explícitamente por `infrastructure/measurements/JdbcMeasurementHistoryProvider`. Traceability no tiene adapters JPA manuales para sus propios repositories.

Environmental Monitoring conserva las entidades JPA en `domain/model`, los records commands/queries, servicios `handle(...)`, eventos Spring síncronos, Resources, Assemblers y ProblemDetail. Para satisfacer el requisito de infraestructura explícita, sus cinco interfaces en `domain/repositories` son contratos sin Spring Data/JPA. Cada uno tiene un adapter `@Repository` en `infrastructure/persistence/adapter` que delega en una interfaz Spring Data en `infrastructure/persistence/repository`. Esa separación también existe en el proyecto para IAM. No se duplican entidades ni se crean mappers o carpetas vacías. No se modificó Traceability.

El historial utiliza `MeasurementHistoryPage` del dominio. La Specification, PageRequest y orden técnico se construyen exclusivamente en el adapter de infraestructura. Los servicios no importan Spring Data, Criteria ni repositories de infraestructura.

Las cinco entidades se ajustan a V1: UUID, enums STRING, columnas snake_case, BigDecimal NUMERIC(6,2), OffsetDateTime y `sensor_node_id` nullable. Las referencias entre entidades se guardan como UUID siguiendo el patrón existente; las FK físicas de V1 conservan su integridad. Organización y facility son referencias lógicas sin relaciones JPA con IAM. `receivedAt` se genera con el Clock existente en UTC; `recordedAt` conserva el instante de captura enviado. La base conserva instantes, no garantiza mantener la representación del offset original.

La ingesta bloquea la fila de la zona con `PESSIMISTIC_WRITE`, valida tenant, zona y punto activos, asociación del sensor opcional, sensor no INACTIVE y umbral de la variable. Guarda y flush antes de publicar eventos. Medición, estado del sensor y efectos de Traceability participan de una misma transacción; un fallo síncrono revierte la operación completa.

## Contrato REST

Base: `/api/v1/environmental-monitoring`. Usa `Authorization: Bearer <accessToken>` del IAM existente. ADMIN escribe; ADMIN y VIEWER leen. No se confía en cabeceras de organización/usuario ni se acepta `organizationId` del body como identidad.

| Método | Ruta | Parámetros/respuesta |
|---|---|---|
| POST | `/measurements` | Body abajo; 201 MeasurementResource y Location |
| GET | `/measurements/{id}` | MeasurementResource; 404 si no pertenece al tenant |
| GET | `/measurements/latest` | `monitoringZoneId` opcional; lista última lectura por punto/variable |
| GET | `/measurements/history` | `monitoringZoneId`, `from`, `to` obligatorios; `monitoringPointId`, `environmentalVariable`, `page`, `size` opcionales |
| GET | `/zones` | Lista de ZoneResource de la organización |
| GET | `/zones/{id}` | ZoneResource; 404 si no pertenece al tenant |

Ejemplo POST (reemplazar UUID por identificadores configurados de la organización):

```json
{
  "monitoringZoneId": "00000000-0000-0000-0000-000000000010",
  "monitoringPointId": "00000000-0000-0000-0000-000000000100",
  "sensorNodeId": null,
  "environmentalVariable": "TEMPERATURE",
  "measuredValue": 7.80,
  "recordedAt": "2026-10-01T11:00:00-05:00"
}
```

La respuesta agrega `id`, `organizationId` desde JWT y `receivedAt` desde Core. HUMIDITY se registra en una llamada/fila independiente. Se rechazan valores que requieran redondeo o excedan NUMERIC(6,2). Los límites del umbral son inclusivos.

Historial: periodo inclusivo, orden `recordedAt ASC, id ASC`; página base cero, tamaño por defecto 100, permitido 1..500. Devuelve `items`, `page`, `size`, `totalElements`, `totalPages`. Umbral inexistente, asociación inactiva, timestamp repetido o periodo/paginación inválidos producen 409. Referencia inexistente o ajena al tenant produce 404; formato/validación del request produce 400. Un sensor opcional debe pertenecer al punto del request.

Swagger: `/swagger-ui.html`; OpenAPI: `/v3/api-docs`.

## Eventos y compatibilidad

El README y `consecutiveDeviationsKeepASingleOpenExcursionWithUpdatedPeak` existentes establecen que las desviaciones sucesivas actualizan el pico de la misma excursión. Se conserva esa semántica: **un DeviationDetected por cada lectura vigente fuera de rango**, incluso OUT_OF_RANGE → OUT_OF_RANGE. No se genera una excursión duplicada; Traceability conserva su `open_key` y consumidor.

Eventos completos del dominio Environmental Monitoring:

- `DeviationDetected(measurementId, organizationId, monitoringZoneId, monitoringPointId, sensorNodeId, environmentalVariable, measuredValue, minimumValue, maximumValue, recordedAt)`.
- `SafeRangeRestored(measurementId, organizationId, monitoringZoneId, monitoringPointId, sensorNodeId, environmentalVariable, measuredValue, recordedAt, restoredAt)`.

`TraceabilityEventAdapter` traduce a los records publicados **existentes** en `traceability/application/integration/InboundEvents`: usa la variable equivalente, double/Instant exigidos por ese contrato y el límite excedido (mínimo o máximo). Esta dependencia de compatibilidad queda limitada a `infrastructure/integration`; ni el dominio ni la aplicación de Environmental Monitoring importan Traceability. No accede a repositories internos de Traceability. No publica IncidentResolved.

Restauración: una lectura normal inicial no genera evento. Tampoco lo generan lecturas normales sucesivas. Solo una transición OUT_OF_RANGE → IN_RANGE del punto puede restaurar; se verifican las últimas lecturas de todos los puntos para esa variable porque Traceability mantiene la excursión por **zona y variable**. Otra variable no bloquea/restaura esa excursión. `restoredAt` es el máximo recordedAt de esas lecturas actuales, para que entregas desordenadas entre puntos no cierren una excursión antes de su inicio. `recordedAt` sigue identificando la lectura que provocó la transición.

## Supuestos y alcance

- Los umbrales y la última medición por punto/variable son la fuente de estado para detectar transiciones; no se consulta el estado interno de Traceability.
- Las lecturas anteriores a la última del punto/variable se guardan como backfill histórico y no publican eventos ni retroceden el sensor. Repetir el mismo timestamp para ese punto/variable devuelve 409, incluso para historial; no se devuelve una respuesta idempotente ni se sobreescribe evidencia.
- La ingesta del Core debe pasar por este caso de uso para conservar el bloqueo y las reglas. La publicación es local y transaccional; no se implementa un broker ni outbox.
- Zonas, puntos, sensores y umbrales deben existir previamente. Se proporcionan modelos, factories y repositories, sin ampliar el alcance con CRUD administrativo o datos ficticios en producción.
- Los sensores pasan a ONLINE al recibir una lectura actual. La detección automática de desconexión/timeout no forma parte de este alcance; las consultas reflejan el status persistido.
- ZoneResource mantiene separado el status administrativo ACTIVE/INACTIVE del estado ambiental NORMAL/NEAR_LIMIT/OUT_OF_RANGE/OFFLINE/NO_DATA. Near limit aplica el margen de 10% del rango del design system. NO_DATA evita interpretar falta de lecturas/umbral como normalidad.
- La proyección entrega `latestMeasurements` por punto y variable, thresholds, puntos, sensores, conteos online/offline/inactive y lastUpdatedAt. Angular puede separar TEMPERATURE/HUMIDITY y definir su política de agregación para zonas con varios puntos. No se conectó el frontend ni se inventó una temperatura promedio.
- OFFLINE refleja que hay nodos configurados y ninguno ONLINE; las últimas lecturas siguen disponibles como evidencia histórica, y el frontend debe mostrar su timestamp y estado.

## Pruebas y verificación

Cobertura: rango inclusivo, precisión numérica, umbral inválido/faltante, tiempos, persistencia sin eventos normales, desviación, desviaciones sucesivas/pico sin excursiones duplicadas, restauración única, independencia de variables, varios puntos, backfill, timestamp repetido, tenant/headers falsificados, integridad punto/sensor, estados inactivos, autenticación/permisos, REST Resources/Location, proyección de zonas, historial inclusivo/paginado, concurrencia, rollback de consumidor, OpenAPI y lectura del historial desde Traceability.

Un test de Hibernate registra únicamente las cinco entidades Environmental Monitoring y valida sus mappings contra V1 sin modificar el esquema. La validación global de todas las entidades detectó una diferencia previa en Traceability (`Excursion.peakValue` double frente a NUMERIC en V4), que se dejó intacta. La configuración de aplicación sigue `ddl-auto: none`.

Comandos requeridos, con Java 21/Maven:

```powershell
mvn clean test
mvn clean package
```

## Publicación de eventos

Environmental Monitoring produce DeviationDetected y SafeRangeRestored. No consume eventos de negocio externos; por ello no necesita `application/eventhandlers`. Traceability sí los necesita porque consume desviaciones, restauraciones e incidentes resueltos. Su `application/ports` abstrae la lectura de historial; Environmental Monitoring ya dispone de ports de persistencia en `domain/repositories`, que no se duplican.

La aplicación de Environmental Monitoring necesita un port de publicación: `application/ports/EnvironmentalMonitoringEventPublisher`, con operaciones tipadas para los dos eventos propios. `RegisterMeasurementCommandService` depende de ese port, sin conocer ApplicationEventPublisher ni consumidores específicos. `infrastructure/integration/SpringEnvironmentalMonitoringEventPublisher` lo implementa publicando los eventos completos en el bus Spring existente, de forma síncrona dentro de la transacción de ingesta.

El antiguo `application/integration/TraceabilityEventAdapter` importaba el contrato y enum de Traceability: esa dependencia estaba en la capa incorrecta. Se trasladó a `infrastructure/integration/TraceabilityEventAdapter`, conservando la traducción a los records existentes. El publisher genérico no conoce Traceability. Otros contextos, incluido un futuro Alert & Incident Management, pueden suscribirse directamente a los eventos completos del productor; no tienen que depender de los records heredados de Traceability.

Persiste una dependencia técnica del puente de compatibilidad hacia el contrato existente de Traceability, aislada en infraestructura. Eliminar también esa dependencia requeriría cambiar la ubicación del Published Language o sus consumidores; no es necesario para invertir la dependencia de la aplicación y se evita modificar Traceability en este refactor.

Las pruebas del refactor verifican que Spring inyecta una sola implementación del port de eventos, que el command service no recibe ApplicationEventPublisher ni tipos de Traceability, y que el publisher entrega los dos eventos completos sin reemplazarlos por contratos del consumidor. Se conservan las pruebas de excursiones, restauración, concurrencia y rollback transaccional.

No se agregó `infrastructure/config`: Spring ya registra el publisher y el puente mediante `@Component`, y reutiliza el bus, Clock y transaction manager existentes. Los adapters JPA siguen en infraestructura y no cambiaron migraciones, endpoints, datos ni semántica de eventos.

## Wiring de persistencia

`@SpringBootApplication` en `com.machineguard.platform` ya escanea los componentes y repositories de todo el proyecto. Spring registra cada adapter anotado con `@Repository` como la única implementación del contrato de dominio y genera el proxy de su repository JPA. La inyección es por constructor; no se necesitan nuevos `@Bean`, `@EnableJpaRepositories`, `@EntityScan` ni una carpeta `config` artificial. Se reutilizan el datasource, EntityManager, transaction manager, Flyway y Clock existentes.

| Contrato de dominio | Adapter de infraestructura | Repository técnico | Tabla V1 |
|---|---|---|---|
| MeasurementRepository | JpaMeasurementRepositoryAdapter | MeasurementJpaRepository | measurements |
| ThresholdRepository | JpaThresholdRepositoryAdapter | ThresholdJpaRepository | thresholds |
| MonitoringZoneRepository | JpaMonitoringZoneRepositoryAdapter | MonitoringZoneJpaRepository | monitoring_zones |
| MonitoringPointRepository | JpaMonitoringPointRepositoryAdapter | MonitoringPointJpaRepository | monitoring_points |
| SensorNodeRepository | JpaSensorNodeRepositoryAdapter | SensorNodeJpaRepository | sensor_nodes |

`RegisterMeasurementCommandService` recibe los cinco contratos. Dentro de su transacción obtiene la zona mediante el contrato de bloqueo, valida punto/sensor, y recupera el Threshold con `findByMonitoringZoneIdAndEnvironmentalVariable(zoneId, variable)`. El adapter de Threshold delega en una consulta derivada de Spring Data por ambas columnas. La evaluación del rango sigue en Threshold, sin reglas de negocio en los repositories.

`MeasurementRepository.save` se implementa con `saveAndFlush`: la lectura tiene UUID y está disponible para los consumidores síncronos antes de publicar eventos. El sensor se actualiza mediante `SensorNodeRepository.save` explícitamente. La persistencia mantiene la transacción de la aplicación y los mismos eventos; los tests existentes de concurrencia y rollback verifican que esta separación no rompe el comportamiento.

## Estructura

El código está en `src/main/java/com/machineguard/platform/environmentalmonitoring/`, con las capas
`application`, `domain`, `infrastructure` e `interfaces`. Las pruebas están en
`src/test/java/com/machineguard/platform/environmentalmonitoring/`.
