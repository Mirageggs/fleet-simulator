#!/usr/bin/env python3
"""Genera un dataset ALTERNO de demostración con el mismo esquema que data-prueba.json.

Red vial ficticia de una operación minera a cielo abierto (coordenadas de la
sierra de Áncash, Perú). El dataset por defecto de la aplicación es el archivo
real de la evaluación (backend/src/main/resources/data/data.json); este demo se
usa para evidenciar la validación de datos incompletos, seleccionándolo con:
    APP_DATA_FILE=classpath:data/data-demo.json

Incluye A PROPÓSITO registros defectuosos, todos etiquetados con
"(DEMO VALIDACIÓN)", y un tramo aislado, para demostrar que el backend valida
datos incompletos y maneja componentes no conectados sin romperse:
  - Un tramo con un solo punto (se descarta con error controlado).
  - Una ubicación con "coor": null (se descarta con error controlado).
  - Un tramo sin conexión con el resto de la red (otro componente conexo).

Uso: python3 tools/generar_datos_demo.py
"""
import json
import math
import os

LAT0, LNG0 = -9.5300, -77.0600          # centro de la operación ficticia
M_LAT = 111_320.0                        # metros por grado de latitud
M_LNG = 111_320.0 * math.cos(math.radians(LAT0))
ESCALA = 0.4                             # compacta la mina para viajes de ~2-3 min


def punto(x_m, y_m):
    """Convierte (x este, y norte) en metros a [lat, lng]."""
    x, y = x_m * ESCALA, y_m * ESCALA
    return [round(LAT0 + y / M_LAT, 6), round(LNG0 + x / M_LNG, 6)]


def curva(a, b, c, n):
    """Polilínea suave: Bézier cuadrática a->c con punto de control b."""
    pts = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * a[0] + 2 * (1 - t) * t * b[0] + t ** 2 * c[0]
        y = (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * b[1] + t ** 2 * c[1]
        pts.append(punto(x, y))
    # Garantiza que los extremos coincidan EXACTAMENTE con los cruces
    pts[0], pts[-1] = punto(*a), punto(*c)
    return pts


# Puntos clave de la mina (metros relativos al centro)
A = (-1500, 900)    # carga: TAJO NORTE
B = (-900, -1100)   # carga: PAD 4C - RAMPA 5
C = (600, 1300)     # carga: STOCK MINERAL 2
X = (-700, 0)       # cruce oeste
N1 = (0, 0)         # cruce central
Y = (800, 200)      # cruce este
D1 = (1900, 1100)   # descarga: BOTADERO NORTE
D2 = (1400, -1000)  # descarga: BOTADERO SUR
D3 = (-100, -1700)  # descarga: CANCHA DE LIXIVIACIÓN 7

routes = [
    {"id_trm_cs": 101, "nombre_tramo": "ACCESO TAJO NORTE",
     "color": "#E53935", "points": curva(A, (-1300, 350), X, 14)},
    {"id_trm_cs": 102, "nombre_tramo": "RAMPA PAD 4C",
     "color": "#8E24AA", "points": curva(B, (-1000, -500), X, 12)},
    {"id_trm_cs": 103, "nombre_tramo": "TRONCAL OESTE",
     "color": "#3949AB", "points": curva(X, (-350, 130), N1, 8)},
    {"id_trm_cs": 104, "nombre_tramo": "ACCESO STOCK MINERAL",
     "color": "#00897B", "points": curva(C, (250, 720), N1, 12)},
    {"id_trm_cs": 105, "nombre_tramo": "TRONCAL ESTE",
     "color": "#F9A825", "points": curva(N1, (420, -60), Y, 8)},
    {"id_trm_cs": 106, "nombre_tramo": "SUBIDA BOTADERO NORTE",
     "color": "#6D4C41", "points": curva(Y, (1500, 720), D1, 12)},
    {"id_trm_cs": 107, "nombre_tramo": "BAJADA BOTADERO SUR",
     "color": "#D81B60", "points": curva(Y, (1350, -380), D2, 12)},
    {"id_trm_cs": 108, "nombre_tramo": "RAMAL CANCHA LIX 7",
     "color": "#455A64", "points": curva(B, (-520, -1520), D3, 10)},
    # Tramo válido pero SIN conexión con el resto (segundo componente conexo)
    {"id_trm_cs": 109, "nombre_tramo": "ACCESO ANTIGUO (AISLADO)",
     "color": "#9E9E9E", "points": curva((-2600, -2300), (-2350, -2500), (-2100, -2600), 6)},
    # Registro defectuoso intencional: un solo punto -> el backend lo descarta
    {"id_trm_cs": 999, "nombre_tramo": "TRAMO DE UN PUNTO (DEMO VALIDACIÓN)",
     "color": "#000000", "points": [punto(0, 300)]},
]

load = [
    {"id": 1, "name": "TAJO NORTE", "coor": punto(*A), "radio": 45},
    {"id": 2, "name": "PAD 4C - RAMPA 5", "coor": punto(*B), "radio": 30},
    {"id": 3, "name": "STOCK MINERAL 2", "coor": punto(*C), "radio": None},
]

dump = [
    {"id": 10, "name": "BOTADERO NORTE", "coor": punto(*D1), "radio": 40},
    {"id": 11, "name": "BOTADERO SUR", "coor": punto(*D2), "radio": None},
    {"id": 12, "name": "CANCHA DE LIXIVIACIÓN 7", "coor": punto(*D3), "radio": 35},
    # Descarga sobre el tramo aislado: ningún par carga->descarga la alcanza,
    # así se demuestra la "decisión visible" de elegir otro destino.
    {"id": 13, "name": "BOTADERO CLAUSURADO (AISLADO)", "coor": punto(-2100, -2600), "radio": 20},
    # Registro defectuoso intencional: sin coordenada -> el backend lo descarta
    {"id": 99, "name": "PUNTO SIN COORDENADA (DEMO VALIDACIÓN)", "coor": None, "radio": 15},
]

data = {"Routes": routes, "Load": load, "Dump": dump}

destino = os.path.join(os.path.dirname(__file__), "..",
                       "backend", "src", "main", "resources", "data", "data-demo.json")
destino = os.path.abspath(destino)
with open(destino, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

total_pts = sum(len(r["points"]) for r in routes)
print(f"OK -> {destino}")
print(f"   {len(routes)} tramos ({total_pts} puntos), {len(load)} cargas, {len(dump)} descargas")
