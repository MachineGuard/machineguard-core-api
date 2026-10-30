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

Desde la raíz del Core:

```powershell
pwsh -File ./scripts/local-testing.ps1 -Action Up
```

El script compila el Core sin volver a ejecutar sus pruebas, instala las
dependencias de Web con `npm ci` si falta la instalación o cambió el lockfile,
inicia su PostgreSQL privado, aplica Flyway y
levanta Core y Angular. Espera que ambos servicios respondan antes de anunciar
que está listo. No depende de Docker ni cambia la configuración de otra base.

| Servicio | Dirección predeterminada |
|---|---|
| Web y login | http://127.0.0.1:18420/login |
| API Core | http://127.0.0.1:18090 |
| Swagger Core | http://127.0.0.1:18090/swagger-ui/index.html |
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

## Verificación del entorno

El 30/10/2026 se ejecutaron **55 pruebas Edge**, incluidas las tres `live_core`,
contra este Core y PostgreSQL 17.5 reales. También se comprobaron los dos roles
mediante el proxy Web, el rechazo de escritura de VIEWER, la conservación de los
identificadores y credenciales al detener/reiniciar, `Up`/`Down` repetidos, el
rechazo de puertos ocupados y la protección ante registros de PID antiguos.
Los tres servicios escucharon exclusivamente en loopback. Esta evidencia es de
testing local; no supone pruebas de hardware ni publicación en Internet.
