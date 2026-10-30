# language: es
@AlertIncident
Característica: Atención de alertas e incidentes desde mediciones reales
  Como responsable de una organización
  deseo atender las desviaciones con identidad y evidencia verificables.

  Antecedentes:
    Dado que existe la organización "North Farm"
    Y que la organización "North Farm" tiene un usuario "admin@north.test" con rol "ADMIN"
    Y que la organización "North Farm" tiene un usuario "viewer@north.test" con rol "VIEWER"
    Y que la organización "North Farm" tiene la zona de monitoreo "Cold Room A"
    Y que "Cold Room A" tiene un rango de temperatura entre 2 y 8 grados
    Y que "admin@north.test" inició sesión
    Y que "viewer@north.test" inició sesión

  @US04 @TS05
  Escenario: Las lecturas sucesivas fuera del rango comparten incidente y alerta
    Cuando "admin@north.test" registra por API en "Cold Room A" una temperatura de 11 grados
    Entonces la respuesta tiene el código 201
    Cuando "admin@north.test" registra por API en "Cold Room A" una temperatura de 12 grados
    Y "admin@north.test" consulta las alertas de "Cold Room A"
    Entonces la lista devuelta tiene 1 elementos
    Y la alerta conserva su zona y el incidente de la primera medición

  @US05
  Escenario: El reconocimiento conserva al responsable y rechaza otro reconocimiento
    Cuando "admin@north.test" registra por API en "Cold Room A" una temperatura de 11 grados
    Y "admin@north.test" reconoce la alerta generada
    Entonces la respuesta tiene el código 201
    Y el reconocimiento pertenece a "admin@north.test"
    Cuando "admin@north.test" reconoce la alerta generada
    Entonces la respuesta tiene el código 409

  @US07
  Escenario: Resolver exige una acción correctiva y condiciones recuperadas
    Cuando "admin@north.test" registra por API en "Cold Room A" una temperatura de 11 grados
    Y "admin@north.test" registra la acción "Reparar refrigeración" en el incidente generado
    Y "admin@north.test" resuelve el incidente generado
    Entonces la respuesta tiene el código 409
    Cuando "admin@north.test" registra por API en "Cold Room A" una temperatura de 5 grados
    Entonces el incidente generado conserva el estado "OPEN" para "admin@north.test"
    Cuando "admin@north.test" resuelve el incidente generado
    Entonces la respuesta tiene el código 200
    Y la resolución conserva la primera medición y al responsable "admin@north.test"

  @TS05 @TS08
  Escenario: Un visualizador consulta alertas pero no puede reconocerlas
    Cuando "admin@north.test" registra por API en "Cold Room A" una temperatura de 11 grados
    Y "viewer@north.test" consulta las alertas de "Cold Room A"
    Entonces la lista devuelta tiene 1 elementos
    Cuando "viewer@north.test" reconoce la alerta generada
    Entonces la respuesta tiene el código 403
