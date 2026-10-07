# language: es
@Traceability @US12
Característica: US12 - Generar reporte de trazabilidad
  Como Encargado de Control de Calidad
  deseo generar un Traceability Report
  para sustentar las condiciones ambientales registradas durante una auditoría interna o externa.

  Antecedentes:
    Dado que existe la organización "North Farm"
    Y que la organización "North Farm" tiene un usuario "calidad@north.test" con rol "ADMIN"
    Y que "calidad@north.test" inició sesión
    Y que la organización "North Farm" tiene la zona de monitoreo "Cold Room A"
    Y que en "Cold Room A" se registró una medición de temperatura de 9.8 grados hace 170 minutos
    Y que en "Cold Room A" se registró una medición de temperatura de 6.2 grados hace 100 minutos

  Escenario: El reporte resume mediciones, excursiones y acciones correctivas del período
    Dado que en "Cold Room A" se detectó una temperatura de 9.8 grados fuera del rango seguro hace 180 minutos
    Y que el incidente de "Cold Room A" se resolvió hace 120 minutos con la acción correctiva "Se ajustó el termostato"
    Cuando "calidad@north.test" genera el reporte de trazabilidad de "Cold Room A" de las últimas 24 horas
    Entonces la respuesta tiene el código 201
    Y el reporte queda en estado "SEALED"
    Y el reporte incluye 1 excursiones cerradas y 2 mediciones
    Y el reporte registra 1 acciones correctivas
    Y el reporte está sellado con un checksum SHA-256 verificable

  Escenario: Un período sin incidentes conserva las mediciones disponibles
    Cuando "calidad@north.test" genera el reporte de trazabilidad de "Cold Room A" de las últimas 24 horas
    Entonces la respuesta tiene el código 201
    Y el reporte incluye 0 excursiones cerradas y 2 mediciones
    Y el reporte registra 0 acciones correctivas

  Escenario: Las excursiones en curso no se incluyen como evidencia cerrada
    Dado que en "Cold Room A" se detectó una temperatura de 10.2 grados fuera del rango seguro hace 15 minutos
    Cuando "calidad@north.test" genera el reporte de trazabilidad de "Cold Room A" de las últimas 24 horas
    Entonces la respuesta tiene el código 201
    Y el reporte incluye 0 excursiones cerradas y 2 mediciones
    Y el reporte indica que excluyó 1 excursiones en curso

  Escenario: Un reporte alterado no supera la verificación de integridad
    Dado "calidad@north.test" genera el reporte de trazabilidad de "Cold Room A" de las últimas 24 horas
    Y que alguien modificó el reporte directamente en la base de datos
    Cuando "calidad@north.test" consulta el reporte generado
    Entonces el sistema detecta que la integridad del reporte no es válida
