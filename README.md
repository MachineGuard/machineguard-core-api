# MachineGuard Core API

API central de MachineGuard. Recibe las lecturas de temperatura y humedad de los
gateways Edge, aplica los umbrales de cada zona, abre alertas e incidentes y guarda
la trazabilidad de las excursiones. La aplicación Web la consume con JWT.

Stack: Java 21, Spring Boot 4.1.2, Spring Security, Spring Data JPA, Flyway y
PostgreSQL. Los identificadores son UUID.

## Ejecutar

Requisitos: Docker Desktop con Docker Compose v2, o Java 21 y Maven para ejecutar sin Docker.
PostgreSQL 16 en Compose y 17 en el entorno local y la CI.

Con Docker:

```powershell
Copy-Item .env.example .env   # reemplace los valores de ejemplo
docker compose up --build
```

La API queda en `http://localhost:8080`. Sin Docker, ejecute `mvn spring-boot:run`
con las variables de base de datos e `IAM_JWT_SECRET` definidas.
Detalle en [docs/ejecucion-local.md](docs/ejecucion-local.md).

Para probar Web y Core juntos en Windows: `pwsh -File ./scripts/local-testing.ps1 -Action Up`
([docs/local-testing.md](docs/local-testing.md)).

Pruebas: `mvn test` (H2, incluye los escenarios BDD). PostgreSQL real y CI en
[docs/pruebas-y-ci.md](docs/pruebas-y-ci.md).

## Módulos

| Módulo | Qué hace | Documentación |
|---|---|---|
| IAM | Login, sesiones JWT, usuarios, organizaciones y credenciales de gateway | [iam.md](docs/iam.md), [gateways.md](docs/gateways.md) |
| Environmental Monitoring | Zonas, puntos, sensores, umbrales y mediciones | [environmental-monitoring.md](docs/environmental-monitoring.md) |
| Ingesta de gateway | Lecturas de Edge con credencial de máquina | [gateway-ingestion.md](docs/gateway-ingestion.md) |
| Alert & Incident | Alertas, incidentes, acciones correctivas y resolución | [alerts.md](docs/alerts.md) |
| Traceability & Quality | Excursiones, no conformidades y reportes sellados | [traceability.md](docs/traceability.md) |
| Solicitudes de piloto | Formulario de la Landing y aprobación por operador | [pilot-requests.md](docs/pilot-requests.md) |

Las migraciones Flyway están en [docs/base-de-datos.md](docs/base-de-datos.md).

## Solicitudes de piloto

La Landing envía solicitudes públicas a `POST /api/v1/pilot-requests`; esa ruta solo
las guarda. Un operador con `X-Operator-Key` las aprueba con `scripts/pilot-operator.ps1`.
La aprobación crea la organización, un administrador inactivo y una invitación de un
solo uso. Core no envía correos. Detalle en [docs/pilot-requests.md](docs/pilot-requests.md).

## Configuración

Copie `.env.example` a `.env` y reemplace los valores; `.env` no debe subirse a Git.
Son obligatorias `MACHINEGUARD_DB_PASSWORD` e `IAM_JWT_SECRET` (al menos 32 bytes).
La lista completa y los valores por defecto están en [docs/configuracion.md](docs/configuracion.md).

## Seguridad básica

- Las escrituras humanas exigen sesión vigente; las consultas validan firma y expiración del JWT.
- Los gateways usan `X-Device-Credential`, con permiso exclusivo de ingesta.
- Compose publica los puertos solo en loopback. OpenAPI y Swagger están desactivados por defecto.

## Documentación

- [Ejecución local](docs/ejecucion-local.md), [configuración](docs/configuracion.md), [base de datos](docs/base-de-datos.md) y [datos de demostración](docs/demo-data.md)
- [IAM](docs/iam.md), [gateways](docs/gateways.md), [ingesta Edge-Core](docs/gateway-ingestion.md) y [solicitudes de piloto](docs/pilot-requests.md)
- [Environmental Monitoring](docs/environmental-monitoring.md), [alertas e incidentes](docs/alerts.md) y [trazabilidad](docs/traceability.md)
- [Testing local en Windows](docs/local-testing.md) y [pruebas y CI](docs/pruebas-y-ci.md)
