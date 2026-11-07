# Datos de demostración

`scripts/demo_data.py` prepara una organización de demostración usando solo la API REST.
No requiere dependencias externas (Python 3.8 o posterior).

Variables:

- `MG_API_URL`: URL de Core (por defecto `http://localhost:8080`).
- `MG_ADMIN_EMAIL` y `MG_ADMIN_PASSWORD`: administrador de la organización.
- `MG_VIEWER_EMAIL` y `MG_VIEWER_PASSWORD`: opcionales. Si se indican, crea un usuario `VIEWER`.

Las credenciales nunca se escriben en el repositorio.

| Comando | Qué hace |
|---|---|
| `python scripts/demo_data.py seed` | Crea tres zonas (dos completas y una a medio configurar), sus puntos, sensores y Safe Ranges, y carga 24 h de mediciones con una excursión ya cerrada. Se puede repetir sin duplicar datos. |
| `python scripts/demo_data.py live` | Publica una medición por sensor cada minuto, como si los nodos estuvieran instalados. Pensado para dejarlo corriendo en el servidor. |
| `python scripts/demo_data.py excursion` | Saca la cámara fría de su Safe Range durante unos minutos y la devuelve, para mostrar la apertura y el cierre de una excursión. |

Mientras `live` está activo, una medición fuera de rango (enviada con `excursion` o desde
Swagger) se respeta durante cinco minutos antes de que las lecturas normales la restablezcan.
