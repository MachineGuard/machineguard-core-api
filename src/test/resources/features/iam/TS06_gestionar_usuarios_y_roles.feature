# language: es
@IAM @TS06
Característica: TS06 - Gestionar organizaciones, usuarios y roles IAM
  Como administrador de una organización
  deseo gestionar usuarios y roles de mi tenant
  para controlar quién puede acceder a MachineGuard.

  Antecedentes:
    Dado que existe la organización "North Farm"
    Y que existe la organización "South Farm"
    Y que la organización "North Farm" tiene un usuario "admin@north.test" con rol "ADMIN"
    Y que la organización "North Farm" tiene un usuario "viewer@north.test" con rol "VIEWER"
    Y que "admin@north.test" inició sesión

  Escenario: Un ADMIN registra un usuario que pertenece solo a su organización
    Cuando "admin@north.test" registra al usuario "  Nuevo.Operario@North.TEST " con rol "VIEWER"
    Entonces la respuesta tiene el código 201
    Y el usuario registrado pertenece a la organización "North Farm"
    Y el usuario registrado tiene el correo "nuevo.operario@north.test"

  Escenario: El correo normalizado es único
    Cuando "admin@north.test" registra al usuario "VIEWER@NORTH.TEST" con rol "VIEWER"
    Entonces la respuesta tiene el código 409

  Escenario: Un ADMIN no puede registrar usuarios en otra organización
    Cuando "admin@north.test" intenta registrar al usuario "intruso@south.test" en la organización "South Farm"
    Entonces la respuesta tiene el código 403

  Escenario: Un VIEWER no puede administrar usuarios
    Dado que "viewer@north.test" inició sesión
    Cuando "viewer@north.test" registra al usuario "otro@north.test" con rol "VIEWER"
    Entonces la respuesta tiene el código 403

  Escenario: Se conserva al menos un administrador activo
    Cuando "admin@north.test" cambia el rol de "admin@north.test" a "VIEWER"
    Entonces la respuesta tiene el código 409
    Cuando "admin@north.test" desactiva al usuario "admin@north.test"
    Entonces la respuesta tiene el código 409
    Y "admin@north.test" sigue siendo un administrador activo

  Escenario: Un usuario inactivo no puede autenticarse
    Dado que la organización "North Farm" tiene un usuario inactivo "baja@north.test" con rol "VIEWER"
    Cuando "baja@north.test" inicia sesión con su contraseña
    Entonces la respuesta tiene el código 401

  Escenario: Un usuario de una organización suspendida no puede autenticarse
    Dado que existe la organización suspendida "Closed Farm"
    Y que la organización "Closed Farm" tiene un usuario "admin@closed.test" con rol "ADMIN"
    Cuando "admin@closed.test" inicia sesión con su contraseña
    Entonces la respuesta tiene el código 401
