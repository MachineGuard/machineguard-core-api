# Testing local en Windows

Este entorno permite probar Web y Core juntos desde esta PC. Usa una base propia,
cuentas sintéticas y servicios ligados a `127.0.0.1`. No publica una URL ni inicia
Cloudflare; tampoco representa un despliegue de producción.

## Requisitos y primer inicio

- Windows y PowerShell 7.2 o posterior (`pwsh`).
- Java 21, Maven, PostgreSQL instalado con `initdb`, `pg_ctl` y `psql`.
- Node/npm y el repositorio `machineguard-web` actualizado en una carpeta hermana
  del Core. Si está en otra ubicación, usar `-WebDirectory`.
- Internet para descargar las dependencias de Maven/npm cuando sea necesario.

Combinación comprobada para este launcher: PowerShell 7.6.5, Java 21.0.12.1,
Maven 3.9.10, PostgreSQL 17.5, Node 26.12.1 y npm 11.19.0 en Windows.
No implica que otras versiones hayan sido verificadas.

Desde la raíz del Core:

```powershell
pwsh -File ./scripts/local-testing.ps1 -Action Up
```

El script compila el Core sin volver a ejecutar sus pruebas, instala las
dependencias de Web con `npm ci` si falta la instalación o cambió el lockfile,
inicia su PostgreSQL privado, aplica Flyway y
levanta Core y Angular. Espera que ambos servicios respondan antes de anunciar
que está listo: comprueba la respuesta protegida de Core, autentica al ADMIN del
bootstrap y verifica el acceso del VIEWER antes de esperar la Web. No depende de
Swagger, Docker ni cambia la configuración de otra base. Las esperas son
acotadas; el login de bootstrap se reintenta espaciado si aún no está disponible
o si recibe 429, sin modificar el límite de autenticación del producto.

OpenAPI está desactivado por defecto. Para habilitarlo deliberadamente solo en
la sesión de testing, define `$env:CORE_OPENAPI_ENABLED = "true"` antes de `Up`
y elimina esa variable después. Con OpenAPI desactivado, `/v3/api-docs` y la
interfaz Swagger no están disponibles.

| Servicio | Dirección predeterminada |
|---|---|
| Web y login | http://127.0.0.1:18420/login |
| API Core | http://127.0.0.1:18090 |
| Swagger Core (solo si se habilita) | http://127.0.0.1:18090/swagger-ui/index.html |
| PostgreSQL privado | 127.0.0.1:55441 |

Web usa el proxy local `/api/v1`; no hay que introducir tokens ni cabeceras de
organización manualmente. Las cuentas son `admin@testing.machineguard.invalid`
y `viewer@testing.machineguard.invalid`. Para consultar sus contraseñas
generadas, ejecutar **solo en tu terminal local**:

```powershell
pwsh -File ./scripts/local-testing.ps1 -Action Credentials | Format-List
```

No copiar esa salida al informe, capturas, Git ni mensajes. Las contraseñas,
credencial de PostgreSQL y clave JWT se conservan como SecureString cifrados
mediante Windows DPAPI en
`%LOCALAPPDATA%/MachineGuard/local-testing/credentials.clixml`; los puede descifrar
el mismo usuario en la misma PC. No son credenciales para usar en producción.
Durante la inicialización de PostgreSQL se crea un archivo de contraseña
temporal legible solo por tu usuario; se elimina al finalizar ese paso, incluso
si falla. No se conserva la contraseña en un `.env` del repositorio.

## Detener, consultar y reiniciar

```powershell
pwsh -File ./scripts/local-testing.ps1 -Action Status
pwsh -File ./scripts/local-testing.ps1 -Action Down
pwsh -File ./scripts/local-testing.ps1 -Action Up
```

`Down` detiene únicamente los procesos registrados por este entorno, verificando
PID, instante de inicio y ejecutable. Conserva la base y las cuentas. Repetir
`Up` con todos los servicios activos informa la dirección sin iniciar otra
instancia. Un puerto ocupado por otro servicio produce un error; no lo cierra.

Los datos, logs y estado están fuera de Git, en la carpeta de runtime indicada.
No existe una acción de borrado de base. Si falta el archivo cifrado de
credenciales y ya hay una base, el script se detiene sin reinicializarla.

Si no detecta los ejecutables, indicar las rutas explícitas:

```powershell
pwsh -File ./scripts/local-testing.ps1 -Action Up `
  -MavenCommand 'C:/ruta/apache-maven/bin/mvn.cmd' `
  -PostgresBin 'C:/Program Files/PostgreSQL/17/bin' `
  -WebDirectory 'C:/ruta/machineguard-web'
```

Para usar otra carpeta/puertos: `-RuntimeDirectory`, `-ApiPort`, `-WebPort` y
`-DatabasePort`. Usar la misma carpeta de runtime al consultar o detener; para
reiniciar con una instalación distinta de PostgreSQL, elegir otro runtime.
No apuntar el runtime a una carpeta de datos PostgreSQL existente.

## Probar las funcionalidades

1. Iniciar sesión como administrador; crear una zona, punto, sensor y rangos.
2. Generar una lectura fuera de rango desde Edge o Swagger. Revisar la alerta
   y su incidente en Web.
3. Reconocer, registrar una acción y comprobar que no se pueda resolver hasta
   recibir una nueva lectura dentro del rango seguro.
4. Resolver y consultar el historial conservado.
5. Entrar como VIEWER y comprobar que puede consultar sin registrar atención.
6. Detener/reiniciar y confirmar que los registros se conservan.

Para pruebas automatizadas con el Edge real, usar sus pruebas `live_core`
configurando `MACHINEGUARD_CORE_URL`, `MACHINEGUARD_CORE_ADMIN_EMAIL` y
`MACHINEGUARD_CORE_ADMIN_PASSWORD` con este entorno sintético. Estas pruebas
crean zonas y mediciones de prueba; no borran los registros que ya existan.

Si falla el arranque, consultar `build-core.log`, `install-web.log`,
`postgres.log`, `core.stdout.log`, `core.stderr.log` y los logs `web.*.log` en
el runtime. Un fallo de arranque intenta detener únicamente sus propios procesos.

## Comprobaciones del entorno

Se comprobaron en este entorno: pruebas `live_core` del Edge contra este Core y PostgreSQL
reales, acceso de los dos roles mediante el proxy Web, rechazo de escritura para VIEWER,
conservación de identificadores y credenciales al detener y reiniciar, `Up` y `Down`
repetidos, rechazo de puertos ocupados y protección ante registros de PID antiguos. Los tres
servicios escuchan solo en loopback. Es evidencia de testing local: no supone pruebas de
hardware ni publicación en Internet.
