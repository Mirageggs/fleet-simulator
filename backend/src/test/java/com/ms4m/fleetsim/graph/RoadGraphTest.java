package com.ms4m.fleetsim.graph;

import com.ms4m.fleetsim.model.GeoPoint;
import com.ms4m.fleetsim.model.RouteSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGraphTest {

    private RoadGraph grafoDePrueba() {
        List<GeoPoint> t1 = List.of(new GeoPoint(0, 0), new GeoPoint(0, 0.002), new GeoPoint(0, 0.004));
        List<GeoPoint> t2 = List.of(new GeoPoint(0, 0.004), new GeoPoint(0.002, 0.004));
        List<GeoPoint> aislado = List.of(new GeoPoint(0.05, 0.05), new GeoPoint(0.05, 0.052));
        return RoadGraph.construir(List.of(
                new RouteSegment(1, "TRONCAL", "#ff0000", t1),
                new RouteSegment(2, "RAMAL", "#00ff00", t2),
                new RouteSegment(3, "AISLADO", "#0000ff", aislado)));
    }

    @Test
    void unificaNodosCompartidosYDetectaComponentes() {
        RoadGraph g = grafoDePrueba();
        // t1 (3 pts) + t2 (comparte 1 pt, aporta 1) + aislado (2 pts) = 6 nodos
        assertEquals(6, g.numNodos());
        assertEquals(2, g.numComponentes());
    }

    @Test
    void dijkstraEncuentraElCaminoMasCortoAtravesDeTramos() {
        RoadGraph g = grafoDePrueba();
        int origen = g.nodoMasCercano(new GeoPoint(0, 0));
        int destino = g.nodoMasCercano(new GeoPoint(0.002, 0.004));
        RoadGraph.Camino c = g.caminoMasCorto(origen, destino);
        assertNotNull(c);
        // 0.004° lng + 0.002° lat en el ecuador ≈ 445 + 222 = 667 m
        assertEquals(667, c.distanciaM(), 15);
        assertTrue(c.puntos().size() >= 3);
    }

    @Test
    void devuelveNullEntreComponentesDistintos() {
        RoadGraph g = grafoDePrueba();
        int origen = g.nodoMasCercano(new GeoPoint(0, 0));
        int aislado = g.nodoMasCercano(new GeoPoint(0.05, 0.05));
        assertNull(g.caminoMasCorto(origen, aislado));
    }
}
