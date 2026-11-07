# Ejecución local

## Con Docker Compose

Requiere Docker Desktop con el daemon iniciado y Docker Compose v2.

```powershell
Copy-Item .env.example .env
```

Reemplace al menos `MACHINEGUARD_DB_PASSWORD` e `IAM_JWT_SECRET`. La clave JWT debe
tener al menos 32 bytes en UTF-8. `.env` está excluido de Git.

```bash
docker compose up --build
```

Compose levanta PostgreSQL 16 y luego la API. La API espera a que PostgreSQL esté
saludable y aplica las migraciones Flyway al iniciar.

| Servicio | Dirección | Variable |
|---|---|---|
| API | `http://localhost:8080` | `API_PORT` |
| PostgreSQL | `127.0.0.1:5432` | `POSTGRES_PORT` |

- `docker compose down` conserva los datos del volumen `machineguard-postgres`.
- `docker compose down -v` elimina también esos datos.
- Dentro del contenedor la API escucha en `0.0.0.0`; el host solo publica en `127.0.0.1`.
- La imagen ejecuta la aplicación como usuario 10001, con `/tmp` como directorio escribible.

## Sin Docker

Requiere Java 21 y Maven. Defina en el entorno `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
e `IAM_JWT_SECRET`, y ejecute:

```bash
mvn spring-boot:run
```

Fuera de Compose la API escucha en `127.0.0.1`, salvo que `SERVER_ADDRESS` indique otra dirección.

## Administrador inicial local

Para crear la organización y su administrador una sola vez, establezca estos valores en
`.env` antes del primer inicio:

```dotenv
IAM_BOOTSTRAP_ENABLED=true
IAM_BOOTSTRAP_ORGANIZATION_NAME=Mi organización
IAM_BOOTSTRAP_ADMIN_NAME=Nombre del administrador
IAM_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
IAM_BOOTSTRAP_ADMIN_PASSWORD=una-frase-segura-de-al-menos-12-caracteres
```

Tras crear el administrador, IAM guarda un marcador persistente para que reiniciar la API
no vuelva a crear el usuario. Si la API se inició antes con el bootstrap desactivado, puede
activarse después mientras la base no tenga organizaciones ni usuarios. Si ya existen
organizaciones o usuarios, el bootstrap se detiene y no modifica esos registros. La API
nunca registra la contraseña.

## OpenAPI y Swagger

Están desactivados por defecto. Con `CORE_OPENAPI_ENABLED=true` quedan disponibles:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Documento OpenAPI: `http://localhost:8080/v3/api-docs`

## Testing local con Web en Windows

El launcher `scripts/local-testing.ps1` levanta Core, PostgreSQL privado y la Web en
puertos propios de loopback. Ver [local-testing.md](local-testing.md).
