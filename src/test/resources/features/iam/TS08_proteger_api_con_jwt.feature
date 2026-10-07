# language: es
@IAM @TS08
Característica: TS08 - Proteger la API con JWT y documentar IAM
  Como integrador de los servicios MachineGuard
  deseo consumir una API protegida y documentada
  para acceder únicamente a los datos de mi organización.

  Antecedentes:
    Dado que existe la organización "North Farm"
    Y que existe la organización "South Farm"
    Y que la organización "North Farm" tiene un usuario "admin@north.test" con rol "ADMIN"
    Y que la organización "North Farm" tiene un usuario "viewer@north.test" con rol "VIEWER"
    Y que la organización "South Farm" tiene un usuario "admin@south.test" con rol "ADMIN"

  Escenario: Una ruta protegida sin JWT responde 401
    Cuando se consulta "/api/v1/traceability/excursions" sin token
    Entonces la respuesta tiene el código 401

  Escenario: Una ruta protegida con un JWT alterado responde 401
    Cuando se consulta "/api/v1/users/me" con un token alterado
    Entonces la respuesta tiene el código 401

  Escenario: Un VIEWER sin el rol requerido no puede generar reportes
    Dado que la organización "North Farm" tiene la zona de monitoreo "Cold Room A"
    Y que "viewer@north.test" inició sesión
    Cuando "viewer@north.test" genera el reporte de trazabilidad de "Cold Room A" de las últimas 24 horas
    Entonces la respuesta tiene el código 403

  Escenario: Las cabeceras de identidad del cliente no exponen datos de otra organización
    Dado que la organización "North Farm" tiene la zona de monitoreo "Cold Room A"
    Y que en "Cold Room A" se detectó una temperatura de 10.5 grados fuera del rango seguro hace 30 minutos
    Y que "admin@south.test" inició sesión
    Cuando "admin@south.test" consulta las excursiones indicando en las cabeceras la organización "North Farm"
    Entonces la lista devuelta tiene 0 elementos

  Escenario: La identidad se obtiene solo del JWT
    Dado que "admin@north.test" inició sesión
    Cuando "admin@north.test" consulta "/api/v1/users/me"
    Entonces la respuesta tiene el código 200

  Escenario: La documentación OpenAPI expone el esquema Bearer y las operaciones IAM
    Cuando se consulta la documentación OpenAPI
    Entonces la respuesta tiene el código 200
    Y la documentación declara el esquema de seguridad "bearerAuth"
    Y la documentación incluye la operación POST "/api/v1/auth/login"
    Y la documentación incluye la operación POST "/api/v1/auth/refresh"
    Y la documentación incluye la operación GET "/api/v1/users/me"
