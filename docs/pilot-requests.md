# Solicitudes de piloto

La Landing envía sus solicitudes de piloto a `POST /api/v1/pilot-requests`. Esta ruta es pública, pero **no crea empresas ni accesos**: solo guarda la solicitud como `PENDING`.

Un operador de MachineGuard aprueba o rechaza cada solicitud. La clave del operador **no es un usuario ni un rol**: no tiene organización, no usa JWT y solo sirve para estas rutas.

## Flujo

1. El cliente envía el formulario de la Landing. Core responde `202` con el `id` y `status: PENDING`.
2. Si ya hay una solicitud pendiente para el mismo correo, Core devuelve esa misma solicitud sin cambiar sus datos.
3. El operador lista las pendientes y aprueba una.
4. Al aprobar, Core usa el flujo IAM existente: crea la organización `ACTIVE`, un administrador `INACTIVE` y una invitación de un solo uso que vence en 24 horas.
5. La respuesta de aprobación trae `invitationToken` **una sola vez**. Solo se guarda su hash.
6. El administrador activa su cuenta con `POST /api/v1/auth/invitations/accept` (`token` y una contraseña de 12 a 72 bytes).

Aprobar dos veces no crea otra organización. La segunda respuesta sale con `invitationIssuedNow: false` y sin token. Rechazar es idempotente; una solicitud aprobada ya no puede rechazarse.

## Rutas

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/v1/pilot-requests` | Público; solo esta ruta exacta |
| GET | `/api/v1/operator/pilot-requests?status=PENDING` | Cabecera `X-Operator-Key` |
| POST | `/api/v1/operator/pilot-requests/{id}/approve` | Cabecera `X-Operator-Key` |
| POST | `/api/v1/operator/pilot-requests/{id}/reject` | Cabecera `X-Operator-Key` |

Sin `IAM_OPERATOR_KEY_HASH`, las rutas de operador responden `404`. Una clave ausente o incorrecta responde `401`. Enviar también `Authorization` responde `401`.

## Configuración

| Variable | Uso |
|---|---|
| `IAM_OPERATOR_KEY_HASH` | SHA-256 en hexadecimal minúsculo de la clave del operador. Vacía deshabilita las rutas de operador. |
| `ACQUISITION_CORS_ALLOWED_ORIGINS` | Orígenes exactos de la Landing, separados por coma (`https://ejemplo.test`). Vacía deshabilita CORS para esta ruta. Sin comodines ni rutas. |
| `ACQUISITION_PILOT_IP_LIMIT` | Solicitudes por dirección IP en la ventana (por defecto 20). |
| `ACQUISITION_PILOT_WINDOW_SECONDS` | Duración de la ventana (por defecto 3600). |

Nunca guarde la clave en el repositorio ni en `.env` versionado. Solo se configura el hash.

### Generar la clave y su hash (PowerShell 7)

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$key = [Convert]::ToBase64String($bytes)
$hash = [BitConverter]::ToString([System.Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($key))).Replace('-', '').ToLower()
"IAM_OPERATOR_KEY_HASH=$hash"   # configure en el servidor
$key                            # guárdela en un gestor privado para el operador
```

En Bash: `KEY=$(openssl rand -base64 32); printf '%s' "$KEY" | sha256sum`.

Para rotar la clave, configure el nuevo hash y reinicie Core. La clave anterior deja de servir de inmediato; los tokens de invitación ya emitidos siguen su propio plazo.

## Herramienta del operador

`scripts/pilot-operator.ps1` usa la variable de entorno `MG_OPERATOR_KEY` y nunca imprime la clave:

```powershell
$env:MG_OPERATOR_KEY = '<clave privada>'
./scripts/pilot-operator.ps1 -Action list
./scripts/pilot-operator.ps1 -Action approve -Id <uuid>
./scripts/pilot-operator.ps1 -Action reject -Id <uuid>
```

`-BaseUrl` acepta otra URL de Core; por defecto usa `http://127.0.0.1:8080`.

## Límites conocidos

- Core no envía correos. El operador entrega el token al administrador por un canal que el equipo debe acordar.
- Si el token se pierde antes de entregarse, esta versión no lo reemite. Requiere una decisión posterior.
- La invitación vence a las 24 horas.
- El límite de solicitudes usa la dirección que ve Core. Detrás de un proxy, todos los clientes comparten una sola dirección.
- Dos envíos simultáneos con el mismo correo pueden devolver `409`; al reintentar se recibe la solicitud existente.
- La Landing sigue en modo demo hasta que se configure `apiBaseUrl`. Este cambio no la modifica.
