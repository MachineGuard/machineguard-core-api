# Pruebas y CI

## Pruebas locales

```bash
mvn test                              # suite completa, incluidos los escenarios BDD
mvn clean verify                      # compilación, pruebas y paquete
mvn test -Dtest=CucumberAcceptanceTest # solo BDD
```

Por defecto las pruebas de integración usan H2 aislado. Son pruebas de API con MockMvc, JWT real
y las migraciones Flyway.

Reporte HTML de Cucumber: `target/cucumber-reports/cucumber.html`.

## Escenarios BDD

Los escenarios Gherkin están en español en `src/test/resources/features`. Los pasos están en
`src/test/java/com/machineguard/platform/acceptance` y se ejecutan contra la API.

## PostgreSQL real

Para ejecutar las pruebas de consultas, persistencia y flujo sobre PostgreSQL, use una base de
pruebas **aislada**. Configure `MACHINEGUARD_TEST_DB_URL` y los system properties
`spring.datasource.driver-class-name`, `spring.datasource.username` y `spring.datasource.password`.
Seleccione, por ejemplo, `AlertIncidentFlowIntegrationTest`, `AlertApiIntegrationTest` y
`AlertPersistenceIntegrationTest`.

Estas clases borran sus fixtures. Nunca las apunte a una base compartida o productiva.

## GitHub Actions

`.github/workflows/ci.yml` se ejecuta en push a `main`, `develop`, `feature/**`, `release/**` y
`hotfix/**`, y en pull request hacia `main` o `develop`. Tiene tres jobs:

| Job | Qué hace |
|---|---|
| `test` | `mvn -B verify`; publica los reportes de Cucumber y Surefire como artefactos |
| `security` | Escaneo de secretos y revisión de dependencias con `scripts/security-check.py` |
| `integration` | Ejecuta el Core con PostgreSQL 17 y las pruebas de integración con Edge |

El job `integration` clona el repositorio Edge en un SHA fijado dentro de `ci.yml`. Necesita el
secreto de solo lectura `MACHINEGUARD_CI_READ_TOKEN`, sin el cual falla al empezar. Los comandos
locales equivalentes están en el repositorio Edge (`scripts/ci.md`).

El job de dependencias y el de secretos fallan ante hallazgos o consultas incompletas.
