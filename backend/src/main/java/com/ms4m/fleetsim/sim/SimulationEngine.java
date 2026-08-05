package com.ms4m.fleetsim.sim;

import com.ms4m.fleetsim.graph.RoadGraph;
import com.ms4m.fleetsim.model.GeoPoint;
import com.ms4m.fleetsim.model.SiteLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Motor de simulación puro (sin Spring, sin hilos): dado un grafo, ubicaciones
 * y una semilla, produce una corrida 100% determinista tick a tick.
 *
 * Asignación de rutas: cada camión elige una carga al azar (con su RNG derivado
 * de la semilla) y una descarga alcanzable dentro del mismo componente conexo;
 * la ruta es el camino más corto (Dijkstra). Si un par carga→descarga no está
 * conectado, se elige otra opción y la decisión queda registrada de forma
 * visible en {@code decisiones}, como pide el enunciado.
 */
public class SimulationEngine {

    /** Estado de un camión para la API y el frontend. */
    public record CamionView(String id, String estado, Double lat, Double lng, double velocidadKmh,
                             String origen, String destino, double distanciaTotalM,
                             double distanciaRecorridaM, double progresoPct) { }

    /** Fotografía completa de la simulación en un instante. */
    public record SimView(String estado, long semilla, long tick, double transcurridoSeg,
                          List<CamionView> camiones, List<String> decisiones) { }

    /** Estadísticas por camión para el reporte de velocidades. */
    public record CamionStats(String id, String estado, String origen, String destino, int muestras,
                              double velMinKmh, double velMaxKmh, double velPromedioKmh,
                              double distanciaKm, double duracionSeg) { }

    private record Sitio(SiteLocation loc, int nodo) { }

    private record Asignacion(Sitio origen, Sitio destino, RoadGraph.Camino camino, String nota) { }

    private final SimConfig cfg;
    private final long semilla;
    private final List<TruckSim> camiones = new ArrayList<>();
    private final List<String> decisiones = new ArrayList<>();
    private long tick = 0;

    public SimulationEngine(long semilla, SimConfig cfg, RoadGraph grafo,
                            List<SiteLocation> cargas, List<SiteLocation> descargas) {
        this.cfg = cfg;
        this.semilla = semilla;

        List<Sitio> cargasUtiles = anclar(grafo, cargas);
        List<Sitio> descargasUtiles = anclar(grafo, descargas);
        if (cargasUtiles.isEmpty() || descargasUtiles.isEmpty()) {
            decisiones.add("No hay cargas o descargas utilizables sobre la red; "
                    + "todos los camiones quedan SIN_RUTA.");
        }

        // Decisión visible: pares carga→descarga imposibles por componentes desconectados.
        for (Sitio d : descargasUtiles) {
            boolean alcanzable = cargasUtiles.stream()
                    .anyMatch(c -> grafo.componenteDe(c.nodo()) == grafo.componenteDe(d.nodo()));
            if (!alcanzable) {
                decisiones.add("La descarga \"" + d.loc().nombre()
                        + "\" está en un componente de la red sin cargas conectadas; se excluye de la asignación.");
            }
        }
        for (Sitio c : cargasUtiles) {
            boolean alcanzable = descargasUtiles.stream()
                    .anyMatch(d -> grafo.componenteDe(d.nodo()) == grafo.componenteDe(c.nodo()));
            if (!alcanzable) {
                decisiones.add("La carga \"" + c.loc().nombre()
                        + "\" está en un componente de la red sin descargas conectadas; se excluye de la asignación.");
            }
        }

        for (int i = 0; i < cfg.numCamiones(); i++) {
            String id = String.format("CAM-%03d", i + 1);
            // RNG propio por camión, derivado de la semilla: reproducible e independiente.
            Random rngCamion = new Random(semilla * 1_000_003L + i);
            Asignacion asignacion = elegirRuta(rngCamion, grafo, cargasUtiles, descargasUtiles);
            if (asignacion == null) {
                decisiones.add(id + ": ningún par carga→descarga está conectado por la red; queda SIN_RUTA.");
                camiones.add(new TruckSim(id, "-", "-", List.of(), rngCamion, cfg));
            } else {
                if (asignacion.nota() != null) decisiones.add(id + ": " + asignacion.nota());
                camiones.add(new TruckSim(id, asignacion.origen().loc().nombre(),
                        asignacion.destino().loc().nombre(), asignacion.camino().puntos(), rngCamion, cfg));
            }
        }
    }

    /** Ancla cada ubicación al nodo más cercano de la red, descartando las que quedan demasiado lejos. */
    private List<Sitio> anclar(RoadGraph grafo, List<SiteLocation> ubicaciones) {
        List<Sitio> utiles = new ArrayList<>();
        for (SiteLocation loc : ubicaciones) {
            int nodo = grafo.numNodos() == 0 ? -1 : grafo.nodoMasCercano(loc.punto());
            double dist = nodo >= 0 ? grafo.distanciaA(loc.punto(), nodo) : Double.MAX_VALUE;
            if (nodo < 0 || dist > cfg.snapMaxMetros()) {
                decisiones.add("La ubicación \"" + loc.nombre() + "\" está a "
                        + (nodo < 0 ? "distancia indeterminada" : Math.round(dist) + " m")
                        + " de la red (máximo " + Math.round(cfg.snapMaxMetros())
                        + " m); se excluye de la simulación.");
            } else {
                utiles.add(new Sitio(loc, nodo));
            }
        }
        return utiles;
    }

    /** Elige (carga, descarga) conectadas usando el RNG del camión; registra descartes en la nota. */
    private Asignacion elegirRuta(Random rng, RoadGraph grafo,
                                  List<Sitio> cargas, List<Sitio> descargas) {
        List<Sitio> orden = new ArrayList<>(cargas);
        Collections.shuffle(orden, rng);
        StringBuilder nota = new StringBuilder();
        for (Sitio carga : orden) {
            List<Sitio> alcanzables = descargas.stream()
                    .filter(d -> d.nodo() != carga.nodo()
                            && grafo.componenteDe(d.nodo()) == grafo.componenteDe(carga.nodo()))
                    .toList();
            if (alcanzables.isEmpty()) {
                nota.append("la carga \"").append(carga.loc().nombre())
                        .append("\" no tiene descargas alcanzables, se eligió otro origen; ");
                continue;
            }
            Sitio descarga = alcanzables.get(rng.nextInt(alcanzables.size()));
            RoadGraph.Camino camino = grafo.caminoMasCorto(carga.nodo(), descarga.nodo());
            if (camino == null || camino.puntos().size() < 2) {
                nota.append("sin camino entre \"").append(carga.loc().nombre()).append("\" y \"")
                        .append(descarga.loc().nombre()).append("\"; ");
                continue;
            }
            String notaFinal = nota.isEmpty() ? null
                    : nota + "se asignó \"" + carga.loc().nombre() + "\" → \""
                    + descarga.loc().nombre() + "\".";
            return new Asignacion(carga, descarga, camino, notaFinal);
        }
        return null;
    }

    /** Avanza un tick (si no ha terminado) y devuelve la fotografía resultante. */
    public synchronized SimView avanzarTick() {
        if (!finalizada()) {
            tick++;
            for (TruckSim camion : camiones) camion.avanzar(cfg);
        }
        return snapshot();
    }

    public synchronized boolean finalizada() {
        return camiones.stream().allMatch(TruckSim::terminado);
    }

    public synchronized SimView snapshot() {
        List<CamionView> vistas = new ArrayList<>();
        for (TruckSim c : camiones) {
            GeoPoint p = c.posicion();
            double progreso = c.distanciaTotalM > 0 ? 100.0 * c.recorridoM / c.distanciaTotalM : 0;
            vistas.add(new CamionView(c.id, c.estado.name(),
                    p == null ? null : p.lat(), p == null ? null : p.lng(),
                    redondear(c.velActualKmh, 1), c.origen, c.destino,
                    redondear(c.distanciaTotalM, 0), redondear(c.recorridoM, 0),
                    redondear(progreso, 1)));
        }
        return new SimView(finalizada() ? "FINALIZADA" : "EN_CURSO", semilla, tick,
                tick * cfg.tickSegundos(), vistas, List.copyOf(decisiones));
    }

    /** Estadísticas por camión: media aritmética de las muestras a intervalos uniformes. */
    public synchronized List<CamionStats> estadisticas() {
        List<CamionStats> stats = new ArrayList<>();
        for (TruckSim c : camiones) {
            int n = c.muestras.size();
            double min = 0, max = 0, suma = 0;
            if (n > 0) {
                min = Double.MAX_VALUE;
                for (double v : c.muestras) {
                    if (v < min) min = v;
                    if (v > max) max = v;
                    suma += v;
                }
            }
            stats.add(new CamionStats(c.id, c.estado.name(), c.origen, c.destino, n,
                    n == 0 ? 0 : redondear(min, 1),
                    n == 0 ? 0 : redondear(max, 1),
                    n == 0 ? 0 : redondear(suma / n, 2),
                    redondear(c.recorridoM / 1000.0, 2),
                    redondear(n * cfg.tickSegundos(), 0)));
        }
        return stats;
    }

    public long semilla() { return semilla; }

    private static double redondear(double v, int decimales) {
        double factor = Math.pow(10, decimales);
        return Math.round(v * factor) / factor;
    }
}
