# language: es
@Traceability @US10
Característica: US10 - Consultar excursiones ambientales
  Como Encargado de Control de Calidad
  deseo consultar las Excursions ocurridas durante un período determinado
  para identificar situaciones en las que los productos estuvieron expuestos a condiciones fuera del rango establecido.

  Antecedentes:
    Dado que existe la organización "North Farm"
    Y que la organización "North Farm" tiene un usuario "calidad@north.test" con rol "ADMIN"
    Y que "calidad@north.test" inició sesión
    Y que la organización "North Farm" tiene la zona de monitoreo "Cold Room A"

  Escenario: Se registra una excursión entre la salida y el retorno al rango seguro
    Dado que en "Cold Room A" se detectó una temperatura de 9.5 grados fuera del rango seguro hace 90 minutos
    Y que la temperatura de "Cold Room A" volvió al rango seguro hace 30 minutos
    Cuando "calidad@north.test" consulta las excursiones de "Cold Room A"
    Entonces la lista devuelta tiene 1 elementos
    Y la primera excursión tiene el estado "CLOSED"
    Y la primera excursión duró 60 minutos

  Escenario: Desviaciones consecutivas mantienen una sola excursión abierta con el valor pico
    Dado que en "Cold Room A" se detectó una temperatura de 8.5 grados fuera del rango seguro hace 20 minutos
    Y que en "Cold Room A" se detectó una temperatura de 11.0 grados fuera del rango seguro hace 15 minutos
    Y que en "Cold Room A" se detectó una temperatura de 9.0 grados fuera del rango seguro hace 5 minutos
    Cuando "calidad@north.test" consulta las excursiones abiertas
    Entonces la lista devuelta tiene 1 elementos
    Y la primera excursión tiene el estado "ONGOING"
    Y la primera excursión tiene un valor pico de 11.0 grados

  Escenario: Se consultan solo las excursiones del período solicitado
    Dado que en "Cold Room A" se detectó una temperatura de 10.0 grados fuera del rango seguro hace 4320 minutos
    Y que la temperatura de "Cold Room A" volvió al rango seguro hace 4300 minutos
    Y que en "Cold Room A" se detectó una temperatura de 9.0 grados fuera del rango seguro hace 120 minutos
    Y que la temperatura de "Cold Room A" volvió al rango seguro hace 100 minutos
    Cuando "calidad@north.test" consulta las excursiones de "Cold Room A" desde hace 24 horas
    Entonces la lista devuelta tiene 1 elementos
    Y la primera excursión tiene un valor pico de 9.0 grados
