# language: es
@IAM @TS07
Característica: TS07 - Autenticar usuarios y gestionar sesiones JWT
  Como usuario registrado
  deseo iniciar y cerrar sesión de forma segura
  para acceder a MachineGuard según mi identidad y rol.

  Antecedentes:
    Dado que existe la organización "North Farm"
    Y que la organización "North Farm" tiene un usuario "calidad@north.test" con rol "ADMIN"

  Escenario: Inicio de sesión con credenciales válidas
    Cuando "calidad@north.test" inicia sesión con su contraseña
    Entonces la respuesta tiene el código 200
    Y recibe un access token de tipo "Bearer" y un refresh token
    Y el access token identifica al usuario "calidad@north.test" con rol "ADMIN"
    Y el refresh token de "calidad@north.test" no se almacena en texto plano

  Escenario: Inicio de sesión con una contraseña incorrecta
    Cuando "calidad@north.test" inicia sesión con la contraseña "clave equivocada 123"
    Entonces la respuesta tiene el código 401

  Escenario: Renovar la sesión rota el refresh token
    Dado que "calidad@north.test" inició sesión
    Cuando "calidad@north.test" renueva su sesión
    Entonces la respuesta tiene el código 200
    Y el nuevo refresh token de "calidad@north.test" es distinto al anterior

  Escenario: Reutilizar un refresh token ya rotado no permite renovar el acceso
    Dado que "calidad@north.test" inició sesión
    Y "calidad@north.test" renueva su sesión
    Cuando "calidad@north.test" reutiliza su refresh token anterior
    Entonces la respuesta tiene el código 401
    Cuando "calidad@north.test" renueva su sesión
    Entonces la respuesta tiene el código 401

  Escenario: Cerrar sesión revoca la sesión renovable
    Dado que "calidad@north.test" inició sesión
    Cuando "calidad@north.test" cierra sesión
    Entonces la respuesta tiene el código 204
    Cuando "calidad@north.test" renueva su sesión
    Entonces la respuesta tiene el código 401

  Escenario: Cambiar la contraseña revoca las sesiones renovables
    Dado que "calidad@north.test" inició sesión
    Cuando "calidad@north.test" cambia su contraseña por "nueva clave segura 2026"
    Entonces la respuesta tiene el código 200
    Cuando "calidad@north.test" renueva su sesión
    Entonces la respuesta tiene el código 401
    Cuando "calidad@north.test" inicia sesión con la contraseña "nueva clave segura 2026"
    Entonces la respuesta tiene el código 200
