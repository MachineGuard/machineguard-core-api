# Configuración

Las variables se leen del entorno. La guía comentada está en `.env.example`; el programa
no carga ese archivo por sí solo. Los valores por defecto son los de `application.yml`
y `compose.yml`.

## Base de datos y red

| Variable | Por defecto | Uso |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/machineguard` | Conexión JDBC |
| `DB_USERNAME` | `machineguard` | Usuario de base |
| `DB_PASSWORD` | vacío | Contraseña de base |
| `MACHINEGUARD_DB_PASSWORD` | obligatoria con Compose | Contraseña del PostgreSQL de Compose |
| `SERVER_ADDRESS` | `127.0.0.1` | Dirección de escucha |
| `API_PORT` / `POSTGRES_PORT` | `8080` / `5432` | Puertos publicados por Compose |

## IAM

| Variable | Por defecto | Uso |
|---|---|---|
| `IAM_JWT_SECRET` | obligatoria | Clave HMAC-SHA256, al menos 32 bytes |
| `IAM_ACCESS_TOKEN_TTL_SECONDS` | `900` | Duración del access token, entre 60 y 3600 |
| `IAM_REFRESH_TOKEN_TTL_DAYS` | `30` | Duración del refresh token |
| `IAM_LOGIN_LIMIT` | `5` | Intentos de login por ventana |
| `IAM_LOGIN_WINDOW_SECONDS` | `60` | Duración de la ventana, de 1 a 3600 |
| `IAM_LOGIN_MAX_KEYS` | `10000` | Máximo de identidades en memoria para el límite |
| `IAM_BOOTSTRAP_ENABLED` | `false` | Crea la organización y el administrador inicial una vez |
| `IAM_BOOTSTRAP_ORGANIZATION_NAME`, `IAM_BOOTSTRAP_ADMIN_NAME`, `IAM_BOOTSTRAP_ADMIN_EMAIL`, `IAM_BOOTSTRAP_ADMIN_PASSWORD` | vacío | Datos del bootstrap |
| `IAM_OPERATOR_KEY_HASH` | vacío | SHA-256 de la clave del operador; vacío desactiva `/api/v1/operator/**` |

## Solicitudes de piloto

| Variable | Por defecto | Uso |
|---|---|---|
| `ACQUISITION_CORS_ALLOWED_ORIGINS` | vacío | Orígenes exactos de la Landing, separados por coma |
| `ACQUISITION_PILOT_IP_LIMIT` | `20` | Solicitudes por IP en la ventana |
| `ACQUISITION_PILOT_WINDOW_SECONDS` | `3600` | Duración de la ventana |
| `ACQUISITION_PILOT_MAX_KEYS` | `10000` | Máximo de IP en memoria para el límite |

## Ingesta y mediciones

| Variable | Por defecto | Uso |
|---|---|---|
| `CORE_MIN_RECORDED_AT` | `2026-01-01T00:00:00Z` | Ninguna captura puede ser anterior |
| `CORE_MAX_READING_AGE_DAYS` | `30` | Antigüedad máxima de una captura |
| `CORE_MAX_FUTURE_SECONDS` | `60` | Adelanto máximo aceptado |
| `CORE_FRESHNESS_MINIMUM_SECONDS` | `30` | Mínimo de la ventana de vigencia |
| `CORE_FRESHNESS_INTERVAL_MULTIPLIER` | `3` | Veces el intervalo de muestreo del sensor |

## Trazabilidad

| Variable | Por defecto | Uso |
|---|---|---|
| `TRACEABILITY_RETENTION_MONTHS` | `60` | Periodo de retención |
| `TRACEABILITY_PURGE_ENABLED` | `false` | Activa el job diario de depuración (03:30 UTC) |
| `TRACEABILITY_MEASUREMENTS_TABLE` | `measurements` | Tabla de la que se lee el historial de mediciones |

## OpenAPI

| Variable | Por defecto | Uso |
|---|---|---|
| `CORE_OPENAPI_ENABLED` | `false` | Activa `/v3/api-docs` y Swagger UI |

## Scripts de demostración

`scripts/demo_data.py` usa `MG_API_URL`, `MG_ADMIN_EMAIL`, `MG_ADMIN_PASSWORD`,
`MG_VIEWER_EMAIL` y `MG_VIEWER_PASSWORD`. Ver [demo-data.md](demo-data.md).

Nunca guarde contraseñas, claves ni tokens en el repositorio. Solo se configura el hash
de la clave del operador.
