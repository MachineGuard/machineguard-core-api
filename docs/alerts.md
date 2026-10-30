# Alertas: persistencia y consultas

Esta entrega implementa el modelo de alerta, persistencia JPA y consultas REST. No genera alertas al recibir mediciones: no registra handlers de `DeviationDetected` ni `SafeRangeRestored`, no clasifica severidad y no implementa incidentes, reconocimiento, escalamiento o resolución.

## Arquitectura

El paquete `com.machineguard.platform.alertincidentmanagement` sigue el patrón de Environmental Monitoring:

- `domain/model/Alert` es una entidad JPA, sin una segunda entidad de persistencia. Mantiene `incidentId` como UUID.
- El contrato `domain/repositories/AlertRepository` se implementa mediante un adapter y un repository Spring Data en infraestructura.
- `AlertQueries` agrupa los records de consulta. `AlertQueryService` opera con `@Transactional(readOnly = true)`.
- El controller convierte resultados a un único `AlertResource`, sin exponer la entidad JPA.

`Alert.raise(incidentId, severity, message, raisedAt)` requiere los cuatro argumentos, rechaza mensajes vacíos o de más de 500 caracteres y establece `PENDING`. La severidad siempre la suministra el llamador; no hay política ni valor por defecto. Esta fábrica no genera automáticamente alertas ni crea incidentes. No hay setters públicos ni operaciones de ciclo de vida.

Se reutiliza V2 sin cambios: FK a `incidents`, unicidad de `alerts.incident_id` y valores de severidad/estado existentes. La unicidad impide duplicar filas, pero no implementa por sí sola la idempotencia del futuro flujo de generación.

## API

Ambos endpoints requieren JWT Bearer y rol `ADMIN` o `VIEWER`. La organización se obtiene de `IamPrincipal`; no se acepta como identidad desde parámetros o cabeceras del cliente.

| Método | Ruta | Parámetros | Respuesta |
|---|---|---|---|
| GET | `/api/v1/alerts` | `monitoringZoneId` UUID y `status` opcionales y combinables | 200 con array; `[]` sin coincidencias |
| GET | `/api/v1/alerts/{alertId}` | UUID obligatorio | 200 con objeto; 404 inexistente o de otra organización |

Listado ordenado por `raised_at DESC, id ASC`, sin paginación en esta entrega. Una zona ajena o inexistente devuelve una lista vacía. UUID o estado inválidos producen 400 (`ProblemDetail`); token ausente o inválido, 401; principal autenticado sin rol permitido, 403.

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "incidentId": "22222222-2222-2222-2222-222222222222",
  "severity": "HIGH",
  "message": "Temperature outside safe range",
  "status": "PENDING",
  "raisedAt": "2026-10-28T15:00:00Z",
  "acknowledgedAt": null,
  "escalatedAt": null
}
```

Severidades: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. Estados: `PENDING`, `ACKNOWLEDGED`, `ESCALATED`, `CLOSED`. El modelo puede leer estados y timestamps escritos por el futuro servicio de Diego; no proporciona transiciones en esta entrega.

Las consultas SQL unen `alerts` con `incidents` y filtran obligatoriamente `i.organization_id` antes de devolver resultados, tanto en listado como en detalle. El filtro opcional por zona también se aplica en SQL. Los parámetros nulos opcionales usan casts explícitos para tipado PostgreSQL. No se duplica el modelo JPA de incidentes ni se agrega `organization_id` a alertas.

## Integración pendiente con Diego

Las siguientes firmas son propuestas documentales; no existen como componentes Java registrados:

```java
interface AlertLifecycleApplicationService {
    void acknowledge(UUID organizationId, UUID alertId,
                     UUID acknowledgedBy, OffsetDateTime acknowledgedAt);
    void escalate(UUID organizationId, UUID alertId, UUID escalatedTo,
                  String reason, OffsetDateTime escalatedAt);
}
```

Diego coordinará las transiciones del incidente y de su alerta, las reglas de reconocimiento/escalamiento y la migración necesaria para identidad y destinatario/motivo. El autor deberá proceder de la identidad validada. No se añadirá un `updateStatus` genérico que evite las reglas. Se debe acordar concurrencia, idempotencia y participación en la misma transacción antes de implementar estas operaciones.

También quedan pendientes la firma de creación/recuperación de incidente por organización/zona/variable, `originMeasurementId`, la recuperación ambiental y la política de severidad. El retorno al rango no debe resolver automáticamente un incidente. V2 no representa todavía todo ese contrato. Esta entrega no modifica V2 ni crea migraciones.

La futura generación deberá garantizar atomicidad entre incidente y alerta, mantener el bloqueo correspondiente hasta el commit y tratar reintentos incluso después de resolver un incidente. No se debe presentar la consulta previa de existencia como garantía suficiente frente a carreras ni continuar una transacción PostgreSQL fallida tras una violación de unicidad.

## Validación

Se agregan tres clases: `AlertDomainTest`, `AlertPersistenceIntegrationTest` y `AlertApiIntegrationTest`. Las pruebas de integración usan las migraciones existentes en H2 aislado en modo PostgreSQL y fixtures SQL para incidentes. Las fixtures no son un servicio de incidentes temporal. Las pruebas de roles usan JWT sintéticos emitidos con la configuración de pruebas; el caso de rol no permitido usa un principal de prueba porque IAM solo define ADMIN/VIEWER.

```text
mvn -Dtest=AlertDomainTest,AlertPersistenceIntegrationTest,AlertApiIntegrationTest test
mvn verify
```

Resultado verificado de esta entrega: 19 pruebas nuevas (4 de dominio, 6 de persistencia y 9 de API), todas aprobadas. La suite completa `mvn verify` terminó con BUILD SUCCESS: 133 pruebas, 0 fallos, 0 errores y 0 omitidas, incluidos 31 escenarios Cucumber (276 pasos). También se completó el empaquetado. Se utilizó Java 21 y Maven 3.9.9. Los errores SQL de FK y unicidad que aparecen durante las pruebas negativas son esperados y están comprobados con assertions.

H2 permite comprobar mappings, FK, unicidad, filtros, serialización y seguridad HTTP; su modo PostgreSQL no reproduce completamente PostgreSQL. Quedan por validar en PostgreSQL aislado el tipado/binding nativo de parámetros UUID nulos, planes de ejecución, precisión temporal y comportamiento de bloqueos/aislamiento bajo concurrencia. Estas pruebas no acreditan generación automática, política de severidad, recuperación ni atomicidad con el futuro servicio de incidentes.

La lista sin paginación puede crecer; este es el contrato limitado aprobado para la primera entrega. La Web no se modifica ni se conecta automáticamente al nuevo recurso.
