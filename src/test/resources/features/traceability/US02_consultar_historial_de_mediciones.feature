# language: es
@Traceability @US02
Característica: US02 - Consultar historial de mediciones
  Como Jefe de Almacén
  deseo consultar el Measurement History de una Monitoring Zone dentro de un período determinado
  para analizar la evolución de sus condiciones ambientales.

  Antecedentes:
    Dado que existe la organización "North Farm"
    Y que la organización "North Farm" tiene un usuario "almacen@north.test" con rol "VIEWER"
    Y que "almacen@north.test" inició sesión
    Y que la organización "North Farm" tiene la zona de monitoreo "Cold Room A"

  Escenario: Se obtienen las mediciones del período solicitado
    Dado que en "Cold Room A" se registró una medición de temperatura de 4.5 grados hace 50 minutos
    Y que en "Cold Room A" se registró una medición de temperatura de 5.1 grados hace 30 minutos
    Y que en "Cold Room A" se registró una medición de temperatura de 6.0 grados hace 10 minutos
    Y que en "Cold Room A" se registró una medición de temperatura de 3.9 grados hace 300 minutos
    Cuando "almacen@north.test" consulta el historial de mediciones de "Cold Room A" de los últimos 60 minutos
    Entonces el historial contiene 3 mediciones

  Escenario: No existen mediciones en el período solicitado
    Dado que en "Cold Room A" se registró una medición de temperatura de 3.9 grados hace 300 minutos
    Cuando "almacen@north.test" consulta el historial de mediciones de "Cold Room A" de los últimos 60 minutos
    Entonces el historial contiene 0 mediciones

  Escenario: Un período inválido es rechazado
    Cuando "almacen@north.test" consulta el historial de mediciones de "Cold Room A" con un período invertido
    Entonces la respuesta tiene el código 400
