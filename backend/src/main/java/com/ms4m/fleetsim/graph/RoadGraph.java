package com.ms4m.fleetsim.graph;

import com.ms4m.fleetsim.model.GeoPoint;
import com.ms4m.fleetsim.model.RouteSegment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Grafo vial no dirigido construido desde las polilíneas de los tramos.
 *
 * Cada vértice de cada polilínea es un nodo; dos tramos quedan conectados
 * cuando comparten un punto (los nodos se unifican redondeando a 1e-6 grados,
 * ~0.1 m, para tolerar diferencias de precisión decimal). Las aristas se
 * ponderan con la distancia haversine, los componentes conexos se calculan con
 * BFS y las rutas más cortas con Dijkstra.
 */
public class RoadGraph {

    /** Camino resultante entre dos nodos: polilínea completa y distancia total. */
    public record Camino(List<GeoPoint> puntos, double distanciaM) { }

    private record Arista(int destino, double pesoM) { }

    private final List<GeoPoint> nodos = new ArrayList<>();
    private final Map<String, Integer> indice = new HashMap<>();
    private final List<List<Arista>> adyacencia = new ArrayList<>();
    private int[] componente = new int[0];
    private int numComponentes = 0;
    private int numAristas = 0;

    private RoadGraph() { }

    public static RoadGraph construir(List<RouteSegment> tramos) {
        RoadGraph g = new RoadGraph();
        for (RouteSegment tramo : tramos) {
            List<GeoPoint> pts = tramo.puntos();
            for (int i = 0; i + 1 < pts.size(); i++) {
                int a = g.nodoDe(pts.get(i));
                int b = g.nodoDe(pts.get(i + 1));
                if (a == b) continue; // puntos duplicados consecutivos
                double peso = GeoUtils.haversineM(g.nodos.get(a), g.nodos.get(b));
                g.adyacencia.get(a).add(new Arista(b, peso));
                g.adyacencia.get(b).add(new Arista(a, peso));
                g.numAristas++;
            }
        }
        g.calcularComponentes();
        return g;
    }

    private int nodoDe(GeoPoint p) {
        String clave = Math.round(p.lat() * 1e6) + ":" + Math.round(p.lng() * 1e6);
        Integer idx = indice.get(clave);
        if (idx != null) return idx;
        idx = nodos.size();
        indice.put(clave, idx);
        nodos.add(p);
        adyacencia.add(new ArrayList<>());
        return idx;
    }

    private void calcularComponentes() {
        componente = new int[nodos.size()];
        Arrays.fill(componente, -1);
        numComponentes = 0;
        for (int inicio = 0; inicio < nodos.size(); inicio++) {
            if (componente[inicio] != -1) continue;
            int c = numComponentes++;
            Deque<Integer> cola = new ArrayDeque<>();
            cola.add(inicio);
            componente[inicio] = c;
            while (!cola.isEmpty()) {
                int u = cola.poll();
                for (Arista e : adyacencia.get(u)) {
                    if (componente[e.destino()] == -1) {
                        componente[e.destino()] = c;
                        cola.add(e.destino());
                    }
                }
            }
        }
    }

    /** Índice del nodo más cercano a un punto arbitrario (o -1 si el grafo está vacío). */
    public int nodoMasCercano(GeoPoint p) {
        int mejor = -1;
        double mejorDist = Double.MAX_VALUE;
        for (int i = 0; i < nodos.size(); i++) {
            double d = GeoUtils.haversineM(p, nodos.get(i));
            if (d < mejorDist) {
                mejorDist = d;
                mejor = i;
            }
        }
        return mejor;
    }

    public double distanciaA(GeoPoint p, int nodo) {
        return GeoUtils.haversineM(p, nodos.get(nodo));
    }

    public int componenteDe(int nodo) {
        return (nodo >= 0 && nodo < componente.length) ? componente[nodo] : -1;
    }

    public int numNodos() { return nodos.size(); }
    public int numAristas() { return numAristas; }
    public int numComponentes() { return numComponentes; }

    /**
     * Ruta más corta con Dijkstra. Devuelve null si origen y destino no están
     * en el mismo componente conexo (no hay camino posible).
     */
    public Camino caminoMasCorto(int origen, int destino) {
        if (origen < 0 || destino < 0) return null;
        if (componenteDe(origen) != componenteDe(destino)) return null;

        double[] dist = new double[nodos.size()];
        int[] previo = new int[nodos.size()];
        Arrays.fill(dist, Double.MAX_VALUE);
        Arrays.fill(previo, -1);
        dist[origen] = 0;

        PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(x -> x[0]));
        pq.add(new double[]{0, origen});
        while (!pq.isEmpty()) {
            double[] tope = pq.poll();
            int u = (int) tope[1];
            if (tope[0] > dist[u]) continue; // entrada obsoleta
            if (u == destino) break;
            for (Arista e : adyacencia.get(u)) {
                double nueva = dist[u] + e.pesoM();
                if (nueva < dist[e.destino()]) {
                    dist[e.destino()] = nueva;
                    previo[e.destino()] = u;
                    pq.add(new double[]{nueva, e.destino()});
                }
            }
        }
        if (dist[destino] == Double.MAX_VALUE) return null;

        LinkedList<GeoPoint> ruta = new LinkedList<>();
        for (int v = destino; v != -1; v = previo[v]) ruta.addFirst(nodos.get(v));
        return new Camino(List.copyOf(ruta), dist[destino]);
    }
}
