# IAM

## Sesiones y roles

El acceso humano usa JWT HMAC-SHA256 con `userId` en `sub`, `organizationId`, `role`
(`ADMIN` o `VIEWER`) y `sid`. La duración por defecto del access token es 15 minutos.
Cada sesión guarda solo el hash SHA-256 del refresh token. El refresh rota el token y
conserva los hashes consumidos: reutilizar uno revoca la sesión. Al cambiar la contraseña
o desactivar a un usuario se revocan sus sesiones renovables.

Toda escritura humana exige una sesión vigente, usuario y organización activos, y el rol
actual. Un JWT con sesión revocada o rol anterior responde 401; un rol vigente sin permiso
responde 403. Las consultas validan firma y expiración del JWT, sin consultar la base en
cada request. Login, refresh e invitación usan sus propias credenciales.

Un access token ya emitido puede seguir aceptándose hasta que expire. `/api/v1/auth/validate`
comprueba además el estado actual de la sesión, el usuario y la organización. No hay
revocación inmediata universal.

## Login

- Límite de cinco intentos por minuto por email normalizado y por IP de conexión
  (`IAM_LOGIN_LIMIT`, `IAM_LOGIN_WINDOW_SECONDS`). Un 429 incluye `Retry-After`.
- No se usa `X-Forwarded-For`: detrás de un proxy, todos los clientes comparten una IP.
- El contador es local a una instancia y tiene memoria acotada. Al llenarse rechaza
  nuevas identidades hasta que expire la ventana. Varias instancias necesitan un control compartido.

## Usuarios y organizaciones

- Los correos se normalizan a minúsculas y son únicos globalmente.
- Solo un administrador activo de la organización registra y administra usuarios.
- El último administrador activo no puede degradarse ni desactivarse. Los cambios
  administrativos bloquean la fila de la organización para evitar carreras.
- Usuarios u organizaciones inactivos o suspendidos no pueden iniciar sesión ni renovarla.
- Las contraseñas tienen entre 12 caracteres y 72 bytes UTF-8 (límite de BCrypt).
- No hay endpoint público para crear organizaciones.

## Endpoints

| Método | Ruta | Descripción | Acceso |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Autentica correo y contraseña; devuelve access y refresh token | Público |
| POST | `/api/v1/auth/refresh` | Rota el refresh token | Público con refresh token |
| POST | `/api/v1/auth/logout` | Revoca la sesión asociada al refresh token | Bearer |
| POST | `/api/v1/auth/password-reset` | Verifica la contraseña actual, la cambia y revoca las sesiones renovables | Bearer |
| POST | `/api/v1/auth/validate` | Verifica JWT, sesión, usuario y organización activos | Bearer |
| POST | `/api/v1/auth/invitations/accept` | Canjea una invitación de administrador de un solo uso por una contraseña | Público con invitación |
| GET | `/api/v1/users/me` | Consulta el perfil autenticado | Bearer |
| POST | `/api/v1/organizations/{organizationId}/users` | Registra un usuario en el tenant autenticado | Bearer + ADMIN |
| PATCH | `/api/v1/users/{userId}/role` | Cambia `ADMIN`/`VIEWER` en el mismo tenant | Bearer + ADMIN |
| PATCH | `/api/v1/users/{userId}/status` | Activa o desactiva un usuario del mismo tenant | Bearer + ADMIN |
| GET | `/api/v1/organizations/{organizationId}` | Consulta la organización del tenant autenticado | Bearer |

Los endpoints de Traceability y de los demás módulos usan la identidad del JWT firmado.
Las cabeceras `X-Organization-Id` y `X-User-Id` se ignoran, y ningún body determina la identidad.

## Solicitudes de piloto

Al aprobar una solicitud, IAM crea una organización y un administrador inactivo, y publica
`InitialAdminInvitationIssued` con un token de un solo uso que vence en 24 horas. Core no
envía correos: el operador entrega el token al contacto. El bootstrap local crea
directamente un administrador activo. Ver [pilot-requests.md](pilot-requests.md).

## Credenciales de gateway

Las identidades de máquina no son usuarios ni tienen rol humano. Ver
[gateways.md](gateways.md) y [gateway-ingestion.md](gateway-ingestion.md).

## Código

Los contratos de IAM se mantienen en `com.machineguard.platform.iam`, con puertos y adapters
de persistencia explícitos. Las rutas REST están en `iam/interfaces/rest`.
