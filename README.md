# MS4M · Simulador de Flota de Camiones Mineros

Aplicación full-stack que simula **5 camiones** viajando desde puntos de **carga (Load)** hacia puntos de **descarga (Dump)** sobre una **red vial de tramos (Routes)**, con velocidades variables, visualización en tiempo real sobre un mapa y **reporte de velocidad promedio por camión** explicado en lenguaje humano.

> Evaluación técnica — Ingeniero de Desarrollo de Software (MS4M).

| Componente | Tecnología | Despliegue sugerido |
|---|---|---|
| Backend | Java 17 · Spring Boot 3.3 · springdoc (Swagger) | Render (Docker) |
| Frontend | React 18 · Vite 8 · Leaflet (react-leaflet) | Vercel |
| Datos | JSON (`Routes` / `Load` / `Dump`), validado al arrancar | Empaquetado o vía `APP_DATA_FILE` |

---

## 1. Arquitectura

```
┌────────────────────────┐         HTTP/JSON + SSE          ┌─────────────────────────────┐
│  Frontend (Vercel)     │ ───────────────────────────────► │  Backend (Render, Docker)   │
│  React + Vite          │  GET /api/network                │  Spring Boot 3 (Java 17)    │
│  Leaflet (mapa)        │  POST /api/simulation/start      │                             │
│                        │  GET /api/simulation/stream (SSE)│  ┌───────────────────────┐  │
│  · mapa en vivo        │  GET /api/simulation (polling)   │  │ DataLoader (validación)│ │
│  · panel de flota      │  GET /api/simulation/report      │  │ RoadGraph (Dijkstra)  │  │
│  · reporte + explicación◄──────────────────────────────── │  │ SimulationEngine      │  │
└────────────────────────┘                                  │  │ ReportService         │  │
                                                            │  └───────────────────────┘  │
                                                            │  Estado: en memoria         │
                                                            └─────────────────────────────┘
```

Flujo: al arrancar, el backend **lee y valida** el JSON (nunca lo modifica), construye un **grafo vial** (nodos = vértices de las polilíneas; tramos que comparten un punto quedan conectados) y calcula **componentes conexos**. Al iniciar una simulación, cada camión recibe un par carga→descarga **conectado** (ruta más corta por **Dijkstra**); si un par no está conectado se elige otro y la decisión queda **registrada de forma visible**. Un planificador avanza la simulación cada *tick* y difunde el estado por **SSE**; el frontend dibuja los camiones moviéndose y, al finalizar (o bajo demanda), muestra el **reporte** con su explicación.

### Estructura del repositorio

```
.
├── backend/                  # API Spring Boot (Java 17)
│   ├── src/main/java/com/ms4m/fleetsim/
│   │   ├── config/           # propiedades, CORS, OpenAPI
│   │   ├── data/             # DataLoader (validación), NetworkDataService
│   │   ├── graph/            # GeoUtils, RoadGraph (BFS + Dijkstra)
│   │   ├── sim/              # SimulationEngine (núcleo puro), TruckSim, SimulationService (SSE)
│   │   ├── report/           # ReportService, HeuristicExplainer, LlmExplainer (opcional)
│   │   └── web/              # controladores REST + manejador de errores
│   ├── src/main/resources/data/data.json       # dataset real de la evaluación
│   ├── src/main/resources/data/data-demo.json  # dataset demo con casos inválidos
│   ├── src/test/java/...     # pruebas JUnit 5
│   └── Dockerfile
├── frontend/                 # React + Vite + Leaflet
│   └── src/ (App, MapView, FleetPanel, ReportPanel, useSimulation, api)
├── tools/generar_datos_demo.py   # generador del dataset de demostración
└── render.yaml               # blueprint de despliegue del backend
```

---

## 2. Ejecución local

### Backend (requiere Java 17+ y Maven 3.8+)

```bash
cd backend
mvn test              # pruebas unitarias
mvn spring-boot:run   # levanta en http://localhost:8080
```

- Documentación interactiva (Swagger): `http://localhost:8080/swagger-ui/index.html`
- Salud del servicio: `http://localhost:8080/api/health`

### Frontend (requiere Node 18+)

```bash
cd frontend
npm install
npm run dev           # levanta en http://localhost:5173
```

Por defecto el frontend apunta a `http://localhost:8080`; para otro backend crea `frontend/.env` con `VITE_API_URL=https://mi-backend` (ver `.env.example`).

### Variables de entorno (todas opcionales, con valores por defecto)

| Variable | Default | Descripción |
|---|---|---|
| `APP_DATA_FILE` | `classpath:data/data.json` | Ruta del archivo de datos (acepta ruta absoluta del sistema) |
| `APP_COORD_ORDER` | `AUTO` | Orden de coordenadas del archivo: `AUTO` \| `LAT_LNG` \| `LNG_LAT` |
| `APP_TRUCKS` | `5` | Número de camiones |
| `APP_TICK_SECONDS` | `1` | Segundos simulados por tick |
| `APP_TIME_SCALE` | `5` | Aceleración del reloj de pared (1 = tiempo real). No altera velocidades, muestras ni estadísticas |
| `APP_SPEED_MIN` / `APP_SPEED_MAX` | `15` / `45` | Rango de velocidades (km/h) |
| `APP_LOADING_SECONDS` / `APP_UNLOADING_SECONDS` | `4` / `4` | Duración de los estados cargando/descargando (0 = desactivados) |
| `APP_SNAP_MAX_METERS` | `200` | Distancia máxima para anclar una ubicación a la red |
| `APP_FAST_PCT` / `APP_SLOW_PCT` | `10` / `10` | Umbrales (%) de la explicación heurística |
| `APP_MIN_SAMPLES` | `10` | Mínimo de muestras para considerar confiable una estadística |
| `APP_CORS_ORIGINS` | `*` | Orígenes CORS permitidos (coma-separados) |
| `APP_LLM_ENABLED` | `false` | Activa la explicación con LLM (opcional) |
| `ANTHROPIC_API_KEY` | — | Clave para el LLM (solo si `APP_LLM_ENABLED=true`) |
| `APP_LLM_MODEL` | `claude-haiku-4-5` | Modelo del LLM |
| `PORT` | `8080` | Puerto HTTP (Render lo inyecta) |

---

## 3. Contrato de la API

Base: `/api`. Respuestas de error siempre con la forma `{"mensaje": "..."}` (409 estado inválido, 404 recurso inexistente, 500 error interno). Documentación navegable en `/swagger-ui/index.html`.

### `GET /api/health`
Salud y resumen de la carga de datos.

```json
{ "estado": "OK", "tramosValidos": 553, "cargas": 30, "descargas": 139,
  "componentesConexos": 4, "errores": 0, "advertencias": 0 }
```

`estado: "DEGRADADO"` si los datos no permiten simular (la app arranca igual y explica por qué).

### `GET /api/network`
Red vial completa para dibujar el mapa.

```json
{
  "tramos": [ { "id": 16347, "nombre": "via DQ_6", "color": "#0000FF",
                "puntos": [[-15.171139, -75.734340], ...] } ],
  "cargas":   [ { "id": 2432, "nombre": "MJ815-C10", "lat": -15.143667, "lng": -75.720901, "radio": 200 } ],
  "descargas":[ { "id": 51847, "nombre": "STK-806-SUL10_C", "lat": -15.156222, "lng": -75.719674, "radio": null } ],
  "grafo": { "nodos": 16497, "aristas": 16585, "componentesConexos": 4 },
  "config": { "velMinKmh": 15, "velMaxKmh": 45, "tickSegundos": 1, "escalaTiempo": 5, "camiones": 5 },
  "validacion": { "operativo": true, "ordenCoordenadas": "LAT_LNG",
                  "errores": ["Tramo \"...\" descartado: ..."], "advertencias": [] }
}
```

- `radio` **puede ser `null`** (así viene en los datos y se respeta).
- `validacion` lista todo registro descartado o corregido: **el archivo nunca se modifica**.

### `POST /api/simulation/start`
Crea la simulación o **reinicia** la existente. Cuerpo opcional:

```bash
curl -X POST http://localhost:8080/api/simulation/start \
     -H "Content-Type: application/json" -d '{"semilla": 42}'
```

Con la misma `semilla`, la corrida es **exactamente reproducible**; sin cuerpo (o `{}`) se usa una semilla aleatoria (visible en la respuesta). Responde el estado inicial (`SimView`). `409` si los datos no son operativos.

### `GET /api/simulation`
Fotografía actual (`SimView`) — usada como *fallback* de polling. Sin simulación previa: `{"estado": "SIN_SIMULACION"}`.

```json
{
  "estado": "EN_CURSO", "semilla": 42, "tick": 37, "transcurridoSeg": 37.0,
  "camiones": [ { "id": "CAM-001", "estado": "EN_RUTA", "lat": -15.1467, "lng": -75.7195,
                  "velocidadKmh": 33.6, "origen": "MJ_476_C1", "destino": "BOT-838-DES-PLAT_TALLER",
                  "distanciaTotalM": 6280, "distanciaRecorridaM": 1520, "progresoPct": 24.2 } ],
  "decisiones": [ "La descarga \"BOT-816-DES_BOXCUT_MJS\" está en un componente de la red sin cargas conectadas; se excluye de la asignación.",
                  "La carga \"MJ392-C1\" está en un componente de la red sin descargas conectadas; se excluye de la asignación." ]
}
```

Estados de camión: `CARGANDO → EN_RUTA → DESCARGANDO → FINALIZADO` (y `SIN_RUTA` si no existe par conectado).

### `GET /api/simulation/stream` (SSE)
`text/event-stream` con un evento `estado` por tick (mismo JSON que arriba) y un evento `fin` al terminar. El frontend lo consume con `EventSource` y, si falla, cae automáticamente a polling.

### `GET /api/simulation/report`
Reporte de velocidades — **promedio = media aritmética de muestras tomadas a intervalos uniformes** (1 por tick, solo `EN_RUTA`). Puede pedirse con la simulación en curso (`parcial: true`).

```json
{
  "generadoEn": "2025-...", "parcial": false, "semilla": 42, "tickSegundos": 1.0,
  "camiones": [ { "id": "CAM-001", "estado": "FINALIZADO", "origen": "MJ_476_C1",
                  "destino": "BOT-838-DES-PLAT_TALLER", "muestras": 683, "velMinKmh": 26.4,
                  "velMaxKmh": 37.6, "velPromedioKmh": 33.10, "distanciaKm": 6.28,
                  "duracionSeg": 683 } ],
  "flota": { "camionesConDatos": 5, "promedioKmh": 29.31, "masRapido": "CAM-003", "masLento": "CAM-004" },
  "explicacion": { "texto": "La flota registró una velocidad promedio de ...", "fuente": "heurística" },
  "decisiones": [ "..." ]
}
```

`404` con mensaje claro si aún no se ejecutó ninguna simulación.

---

## 4. Datos: `data-prueba.json` incluido y dataset demo alterno

El dataset **por defecto** es el archivo real de la evaluación, embebido como `backend/src/main/resources/data/data.json` (553 tramos · 30 cargas · 139 descargas · 17,143 puntos, orden `[lat, lng]`). Lo que el sistema encuentra en él (fijado por `RealDatasetTest`):

- **4 componentes conexos** (16,497 nodos): una red principal, una zona auxiliar (tramos `ZZ_*`) y dos ramales sueltos.
- **1 carga varada** (`MJ392-C1`, en la zona auxiliar sin descargas) y **1 descarga varada** (`BOT-816-DES_BOXCUT_MJS`, en un ramal sin cargas): el motor las excluye y lo registra como **decisión visible** — el caso real de "pares no conectados" que anticipa el enunciado.
- **8 descargas con `radio: null`** y **2 nombres de descarga repetidos** (`STK-762-OXI3_N_B`, `STK-762-OXI4M`): ambos permitidos por el enunciado y manejados (el radio null se respeta; internamente se distingue por `id`).
- 0 registros inválidos: `errores` queda vacío y `operativo: true`.

También se incluye un **dataset demo alterno** (`data-demo.json`, generado por `tools/generar_datos_demo.py`) con registros defectuosos intencionales —etiquetados `(DEMO VALIDACIÓN)`— para evidenciar la validación con respuesta controlada (tramo de 1 punto, ubicación sin coordenada, descarga sobre un tramo aislado):

```bash
APP_DATA_FILE=classpath:data/data-demo.json mvn spring-boot:run
```

Para apuntar a cualquier otro archivo sin recompilar: `APP_DATA_FILE=/ruta/absoluta/otro.json`. El validador tolera claves alternativas (`Routes/routes`, `Load/loads`, `coor/coord`…), detecta el orden de coordenadas y reporta en `/api/network` todo lo que descarte o corrija. **Nunca** escribe sobre el archivo.

---

## 5. Decisiones de diseño (y alternativas consideradas)

| Decisión | Alternativas | Por qué así |
|---|---|---|
| **Grafo + Dijkstra** sobre los vértices de las polilíneas, unificando nodos a 1e-6° | A* (innecesario a esta escala); ir "en línea recta" entre carga y descarga | El enunciado exige viajar **por los tramos** y detectar pares no conectados; Dijkstra da la ruta más corta real y los **componentes conexos** (BFS) responden "¿están conectados?" con rigor |
| **SSE** para el tiempo real, con **polling automático** de respaldo | WebSocket; solo polling | El flujo es unidireccional servidor→cliente: SSE es más simple (HTTP puro, reconexión nativa de `EventSource`) y el respaldo cubre proxies que lo bloqueen |
| **Estado en memoria**, una simulación activa | Base de datos; múltiples simulaciones concurrentes | Lo permite el enunciado y simplifica el reinicio (`POST /start` = reiniciar). *Trade-off*: se pierde al redesplegar (aceptado y documentado) |
| **Semilla + RNG por camión** (`semilla*1_000_003 + i`) | Un RNG global compartido | Reproducibilidad total (misma semilla → misma corrida, verificado por prueba automática) e independencia entre camiones |
| Velocidad: base propia por camión ± variación por tick, acotada al rango | Velocidad constante; ruido puro | Cumple "velocidades variables", genera promedios distintos por camión (reporte comparativo con contenido) y respeta los límites configurados |
| **Escala de tiempo ×5** por defecto (solo reloj de pared) | Tiempo real 1:1; acortar tramos o subir velocidades | En el dataset real el viaje mediano es de 3 km (~6 min a 30 km/h) y el máximo ~20 min: a 1:1 la espera del reporte final es impráctica. La escala acelera el planificador sin tocar el motor: mismas muestras, promedios y determinismo; `APP_TIME_SCALE=1` restaura el 1:1 y la UI indica la escala activa |
| **Anclaje (snap) a ≤ 200 m** de la red, configurable | Exigir coincidencia exacta; anclar sin límite | Los datos reales traen ubicaciones fuera de la línea; un límite explícito evita "teletransportes" absurdos y toda exclusión queda **registrada como decisión** |
| Detección **AUTO** del orden `[lat,lng]` vs `[lng,lat]` | Fijar un orden y confiar | El enunciado no garantiza el orden; en Perú (\|lng\|≈77 vs \|lat\|≈9–13) la heurística es inequívoca, y puede forzarse con `APP_COORD_ORDER` |
| **Núcleo de simulación puro** (sin Spring) | Lógica dentro de los servicios web | Probable en aislamiento (JUnit + harness), determinista y reutilizable |
| Explicación **heurística por defecto**, LLM **opcional con respaldo** | Solo LLM; solo plantilla | El reporte nunca depende de un servicio externo: si el LLM falta o falla, siempre hay explicación útil, y `fuente` transparenta cuál se usó |
| Backend en **Render (Docker)** y frontend en **Vercel** | Todo en Vercel | Vercel serverless no mantiene **estado en memoria** ni conexiones SSE largas; Render corre el jar como proceso persistente. *Trade-off* del plan gratuito: ~50 s de arranque en frío tras inactividad |

## 6. Supuestos

- Cada camión realiza **un** viaje carga→descarga por corrida (los estados `CARGANDO`/`DESCARGANDO` opcionales están activados con 4 s por defecto; con `APP_LOADING_SECONDS=0` y `APP_UNLOADING_SECONDS=0` se desactivan).
- Los tramos son **bidireccionales** y transitables completos; dos tramos se conectan si comparten un vértice (con tolerancia de redondeo de ~0.1 m).
- La asignación carga→descarga es **aleatoria (con semilla)** entre pares conectados; no se optimiza producción ni se evita repetir destinos.
- La "muestra a intervalos uniformes" se toma **una vez por tick** mientras el camión está `EN_RUTA` (las esperas no cuentan para el promedio de velocidad).
- Nombres de ubicaciones pueden repetirse (el enunciado lo permite): internamente se distingue por `id` + nodo anclado.
- El promedio pedido es la **media aritmética simple** de esas muestras (equivale al promedio temporal al ser intervalos uniformes).

## 7. Limitaciones conocidas

- **Estado volátil**: al reiniciar el backend se pierde la simulación (aceptado por el enunciado; mitigable con Redis/BD si se necesitara).
- Una sola simulación activa por instancia (multiusuario la comparte).
- El movimiento entre ticks se interpola linealmente en el navegador (transición CSS): con ticks muy largos el movimiento se percibe por saltos suavizados.
- El anclaje usa el **vértice** más cercano, no la proyección perpendicular sobre la arista (suficiente con polilíneas densas como las de los datos).
- Sin autenticación: la API es pública para la demo.
- Plan gratuito de Render: primer request tras inactividad tarda ~50 s (arranque en frío).

## 8. Pruebas

```bash
cd backend && mvn test
```

Cobertura de lo crítico:

- `RoadGraphTest` — unificación de nodos, componentes conexos, Dijkstra, `null` entre componentes.
- `DataLoaderTest` — descarte de registros inválidos con errores controlados, `radio: null` conservado, detección de orden de coordenadas, JSON corrupto y colecciones faltantes sin excepción.
- `SimulationDeterminismTest` — misma semilla ⇒ corridas idénticas; semillas distintas ⇒ difieren; muestras dentro del rango; **decisión visible** al excluir una descarga aislada.
- `GeoUtilsTest`, `HeuristicExplainerTest` — distancia/interpolación y contenido de la explicación (rápido/lento, advertencias, parcial).
- `RealDatasetTest` — regresión sobre el **dataset real**: 553/30/139 registros válidos, orden `[lat, lng]`, 4 componentes, exclusión con decisión visible de `MJ392-C1` y `BOT-816-DES_BOXCUT_MJS`, y determinismo de la corrida.

El frontend se verifica con `npm run build` (compilación estricta de Vite).

## 9. Despliegue y demostración

La solución se encuentra publicada y puede probarse sin instalación local.

| Recurso | URL |
|---|---|
| **Aplicación web** | [fleet-simulator-wine.vercel.app](https://fleet-simulator-wine.vercel.app/) |
| **API pública** | [ms4m-fleet-sim-api.onrender.com](https://ms4m-fleet-sim-api.onrender.com) |
| **Swagger / OpenAPI** | [swagger-ui/index.html](https://ms4m-fleet-sim-api.onrender.com/swagger-ui/index.html) |
| **Estado del servicio** | [api/health](https://ms4m-fleet-sim-api.onrender.com/api/health) |
| **Repositorio** | [github.com/Mirageggs/fleet-simulator](https://github.com/Mirageggs/fleet-simulator) |

### Arquitectura de despliegue

```text
GitHub
├── backend/  ── Docker ──► Render
└── frontend/ ── Vite   ──► Vercel
                              │
                              └── VITE_API_URL apunta a Render
```

El backend se ejecuta en Render como un proceso persistente porque conserva el estado de la simulación en memoria y mantiene una conexión SSE durante las actualizaciones. El frontend se publica en Vercel como una aplicación estática compilada con Vite.

> **Nota:** el plan gratuito de Render puede suspender el servicio después de un periodo de inactividad. La primera solicitud puede tardar algunos segundos mientras la instancia vuelve a iniciar.

---

## 10. Respuestas a las preguntas del enunciado

### 10.1 Enfoque general

La solución se planteó como una aplicación full stack separada en dos componentes:

- **Backend:** Java 17 con Spring Boot.
- **Frontend:** React con JavaScript, Vite y Leaflet.

Al iniciar, el backend carga y valida el archivo JSON sin modificarlo. Después convierte los tramos viales en un grafo, calcula sus componentes conexos y prepara las ubicaciones de carga y descarga que pueden utilizarse en una simulación.

Cuando se inicia una corrida, el sistema crea exactamente cinco camiones con identificadores estables. Para cada camión selecciona un origen de carga y un destino de descarga alcanzable, calcula el recorrido sobre la red y actualiza periódicamente su posición, velocidad y estado.

El frontend consume la red vial mediante HTTP, representa los elementos sobre Leaflet y recibe el estado de la flota mediante Server-Sent Events. Al finalizar —o durante una consulta parcial— muestra el reporte de velocidades y una explicación generada mediante reglas heurísticas.

### 10.2 Red vial y selección de recorridos

#### Solución aplicada

Los puntos consecutivos de cada polilínea se modelan como conexiones transitables. La distancia geográfica entre puntos se utiliza como costo de las aristas.

Se emplea:

- **BFS** para identificar componentes conexos.
- **Dijkstra** para obtener el camino de menor distancia entre carga y descarga.
- **Snap configurable** para asociar cada ubicación con un nodo cercano de la red.

Antes de asignar un recorrido se comprueba que origen y destino pertenezcan a componentes compatibles. Cuando una ubicación no tiene una contraparte alcanzable, se excluye y la decisión se expone en la respuesta de la simulación.

#### Alternativas consideradas

**A\*** habría sido una alternativa válida y podría reducir la cantidad de nodos explorados en redes mucho mayores si se utiliza una heurística geográfica adecuada. Para la escala de este dataset, Dijkstra resulta suficientemente claro, determinista y fácil de probar.

Mover los camiones en línea recta no se consideró una alternativa aceptable porque ignoraría los tramos disponibles y contradiría el requisito principal del ejercicio.

#### Mejora posible

Con más tiempo, el anclaje de ubicaciones podría proyectarse sobre el punto más cercano de una arista, en lugar de utilizar solamente el vértice más próximo.

### 10.3 Simulación y actualizaciones en tiempo real

Cada camión mantiene un estado propio:

```text
CARGANDO → EN_RUTA → DESCARGANDO → FINALIZADO
```

Durante `EN_RUTA`, la distancia avanzada en cada tick se obtiene a partir de la velocidad actual y del tiempo simulado transcurrido. La posición se interpola sobre los segmentos del recorrido, evitando saltos fuera de la red vial.

Las velocidades varían dentro de un rango configurable de **15 a 45 km/h**. Cada ejecución puede recibir una semilla opcional:

- Con la misma semilla, la simulación es reproducible.
- Sin semilla, el backend genera una automáticamente y la devuelve en la respuesta.

La escala temporal `APP_TIME_SCALE=5` acelera el reloj de pared para facilitar la demostración. No modifica la distancia simulada por tick, las muestras registradas ni las estadísticas finales.

#### Elección de SSE

Se eligió **Server-Sent Events** porque el flujo principal es unidireccional: el backend publica cambios y el navegador los recibe. Frente a WebSocket, SSE requiere menos complejidad para este caso y cuenta con reconexión nativa mediante `EventSource`.

Como respaldo, el frontend utiliza polling cuando la conexión SSE no está disponible.

### 10.4 Reporte y explicación heurística

Mientras cada camión está en ruta, el sistema registra una muestra de velocidad por tick. Como los intervalos son uniformes, la velocidad promedio se calcula mediante la media aritmética:

```text
velocidad promedio = suma de velocidades / cantidad de muestras
```

El reporte incluye por camión:

| Indicador | Descripción |
|---|---|
| **Muestras** | Cantidad de mediciones registradas |
| **Velocidad mínima** | Menor velocidad observada |
| **Velocidad máxima** | Mayor velocidad observada |
| **Velocidad promedio** | Media aritmética de las muestras |
| **Distancia** | Longitud total del recorrido |
| **Duración** | Tiempo simulado del viaje |

También se calcula el promedio general de la flota y se identifican los camiones con mayor y menor promedio.

La explicación en lenguaje humano utiliza una heurística determinista. Esta compara los resultados con el promedio de la flota, identifica diferencias relevantes y advierte cuando existen pocas muestras. De esta forma, ninguna cifra depende de un modelo generativo.

Un LLM podría utilizarse únicamente para mejorar la redacción a partir de los datos estructurados. La heurística debe mantenerse como respuesta de respaldo.

### 10.5 Validación y manejo de errores

El archivo de entrada se trata como una fuente inmutable. Los registros inválidos no se corrigen ni se eliminan silenciosamente.

La validación contempla:

- Colecciones faltantes o JSON corrupto.
- Tramos con geometría insuficiente.
- Coordenadas ausentes o inválidas.
- Radios con valor `null`.
- Nombres repetidos.
- Ubicaciones alejadas de la red.
- Componentes sin cargas o descargas compatibles.

Los errores y advertencias se incluyen en `/api/network`, mientras que la API utiliza códigos HTTP y mensajes consistentes para estados no válidos.

### 10.6 Persistencia y alcance

El estado se conserva en memoria porque el enunciado permite esta simplificación y solo requiere una simulación activa. Esto reduce la complejidad de despliegue y permite reiniciar la corrida mediante `POST /api/simulation/start`.

La principal consecuencia es que una simulación se pierde al reiniciar o redesplegar el backend.

Una evolución natural sería incorporar Redis o una base de datos para:

- Conservar históricos.
- Recuperar simulaciones después de un reinicio.
- Ejecutar varias simulaciones en paralelo.
- Separar el estado por usuario o sesión.

### 10.7 Mejoras con más tiempo

Las siguientes mejoras ampliarían la calidad y escalabilidad del producto:

1. Migrar el frontend de JavaScript a TypeScript.
2. Incorporar pruebas unitarias y de integración para React.
3. Proyectar las ubicaciones sobre la arista más cercana.
4. Persistir simulaciones e históricos.
5. Admitir varias simulaciones concurrentes.
6. Mejorar accesibilidad, experiencia móvil y rendimiento del mapa.
7. Agregar métricas, trazas y logs estructurados.
8. Configurar integración continua para ejecutar pruebas y compilaciones.
9. Aplicar rate limiting y controles adicionales sobre la API pública.

---

## 11. Uso de Inteligencia Artificial

La inteligencia artificial se utilizó de forma intensiva y transparente durante el desarrollo.

| Etapa | Participación |
|---|---|
| **Elección tecnológica** | Seleccioné Java con Spring Boot para el backend. |
| **Insumos** | Proporcioné el archivo JSON real de la evaluación. |
| **Generación inicial** | Fable/Claude generó la mayor parte de la arquitectura, el backend, el frontend, las pruebas y una primera versión del README. |
| **Validación local** | Ejecuté las pruebas, levanté ambos proyectos en Ubuntu y comprobé los endpoints, el mapa, los cinco camiones, SSE, la escala ×5 y el reporte. |
| **Correcciones** | Resolví la incompatibilidad entre Vite 8 y `@vitejs/plugin-react`, y configuré la comunicación entre Vercel y Render. |
| **Despliegue** | Publiqué el backend en Render, el frontend en Vercel y verifiqué la solución completa desde sus URLs públicas. |
| **Documentación** | Revisé las decisiones, limitaciones y respuestas finales para representar con precisión el proceso realizado. |

No afirmo haber escrito manualmente la totalidad del código. La IA actuó como generador y asistente técnico; mi responsabilidad fue suministrar los insumos, ejecutar la solución, comprobar su funcionamiento, corregir los problemas encontrados, desplegarla y comprender las decisiones principales necesarias para defender el resultado.

La explicación del reporte no depende de un LLM: utiliza una heurística determinista y verificable. El soporte para LLM es opcional y conserva siempre la heurística como mecanismo de respaldo.

---

## 12. Estado de la entrega

- [x] Backend en Java y Spring Boot.
- [x] Frontend en React y Leaflet.
- [x] Dataset real cargado y validado.
- [x] Cinco camiones sobre recorridos de la red.
- [x] Velocidades variables y semilla opcional.
- [x] Actualizaciones mediante SSE con polling de respaldo.
- [x] Reporte por camión y explicación heurística.
- [x] Pruebas del backend.
- [x] Documentación OpenAPI/Swagger.
- [x] Frontend desplegado en Vercel.
- [x] Backend desplegado en Render.
- [x] Repositorio público e instrucciones de ejecución.
