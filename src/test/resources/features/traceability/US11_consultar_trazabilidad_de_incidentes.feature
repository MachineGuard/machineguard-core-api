# language: es
@Traceability @US11
Característica: US11 - Consultar trazabilidad de incidentes
  Como Encargado de Control de Calidad
  deseo consultar el historial de Incidents
  para disponer de evidencia de las desviaciones y acciones tomadas durante un período de almacenamiento.

  # Alcance del Sprint 1: Traceability recibe IncidentResolved y vincula la acción correctiva a la excursión.
  # La consulta de Alert y Acknowledgement se cubrirá cuando se implemente Alert & Incident Management.

  Antecedentes:
    Dado que existe la organización "North Farm"
    Y que la organización "North Farm" tiene un usuario "calidad@north.test" con rol "ADMIN"
    Y que "calidad@north.test" inició sesión
    Y que la organización "North Farm" tiene la zona de monitoreo "Cold Room A"
    Y que en "Cold Room A" se detectó una temperatura de 9.5 grados fuera del rango seguro hace 60 minutos

  Escenario: La excursión muestra el incidente y su acción correctiva
    Dado que el incidente de "Cold Room A" se resolvió hace 20 minutos con la acción correctiva "Se reemplazó el compresor"
    Cuando "calidad@north.test" consulta el detalle de la última excursión de "Cold Room A"
    Entonces la respuesta tiene el código 200
    Y el detalle muestra la acción correctiva "Se reemplazó el compresor"

  Escenario: Un lote afectado se registra en cuarentena y liberarlo exige justificación
    Dado que la temperatura de "Cold Room A" volvió al rango seguro hace 10 minutos
    Cuando "calidad@north.test" registra el lote "L-2026-001" de "Insulina" como no conformidad de la última excursión de "Cold Room A"
    Entonces la respuesta tiene el código 201
    Y el lote queda con la disposición "QUARANTINED"
    Cuando "calidad@north.test" libera el lote sin justificación
    Entonces la respuesta tiene el código 409
    Cuando "calidad@north.test" libera el lote con la justificación "Laboratorio confirmó la estabilidad del producto"
    Entonces la respuesta tiene el código 200
    Y el lote queda con la disposición "RELEASED"
    Cuando "calidad@north.test" consulta el detalle de la última excursión de "Cold Room A"
    Entonces el detalle muestra 1 no conformidades
