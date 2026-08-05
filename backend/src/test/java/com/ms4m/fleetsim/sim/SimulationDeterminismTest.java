package com.ms4m.fleetsim.sim;

import com.ms4m.fleetsim.graph.RoadGraph;
import com.ms4m.fleetsim.model.GeoPoint;
import com.ms4m.fleetsim.model.RouteSegment;
import com.ms4m.fleetsim.model.SiteLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationDeterminismTest {

    private RoadGraph grafo() {
        List<GeoPoint> t1 = List.of(new GeoPoint(0, 0), new GeoPoint(0, 0.002), new GeoPoint(0, 0.004));
        List<GeoPoint> t2 = List.of(new GeoPoint(0, 0.004), new GeoPoint(0.002, 0.004));
        return RoadGraph.construir(List.of(
                new RouteSegment(1, "TRONCAL", "#ff0000", t1),
                new RouteSegment(2, "RAMAL", "#00ff00", t2)));
    }

    private SimulationEngine motor(long semilla, RoadGraph g) {
        var cargas = List.of(new SiteLocation(1, "CARGA 1", new GeoPoint(0, 0), 20.0, "LOAD"));
        var descargas = List.of(new SiteLocation(10, "DESCARGA 1", new GeoPoint(0.002, 0.004), null, "DUMP"));
        SimConfig cfg = new SimConfig(5, 1, 15, 45, 2, 2, 200);
        return new SimulationEngine(semilla, cfg, g, cargas, descargas);
    }

    @Test
    void mismaSemillaProduceCorridasIdenticas() {
        RoadGraph g = grafo();
        SimulationEngine e1 = motor(42, g);
        SimulationEngine e2 = motor(42, g);
        for (int i = 0; i < 300 && !e1.finalizada(); i++) {
            assertEquals(e1.avanzarTick(), e2.avanzarTick(), "divergencia en el tick " + (i + 1));
        }
        assertTrue(e1.finalizada(), "la simulación debe terminar");
        assertEquals(e1.estadisticas(), e2.estadisticas());
    }

    @Test
    void semillasDistintasProducenCorridasDistintas() {
        RoadGraph g = grafo();
        SimulationEngine e1 = motor(42, g);
        SimulationEngine e2 = motor(7, g);
        for (int i = 0; i < 30; i++) {
            e1.avanzarTick();
            e2.avanzarTick();
        }
        assertNotEquals(e1.snapshot().camiones(), e2.snapshot().camiones());
    }

    @Test
    void registraMuestrasSoloEnRutaYCalculaEstadisticas() {
        RoadGraph g = grafo();
        SimulationEngine e = motor(42, g);
        while (!e.finalizada()) e.avanzarTick();
        for (var s : e.estadisticas()) {
            assertTrue(s.muestras() > 0);
            assertTrue(s.velMinKmh() >= 15 - 1e-9);
            assertTrue(s.velMaxKmh() <= 45 + 1e-9);
            assertTrue(s.velPromedioKmh() >= s.velMinKmh() && s.velPromedioKmh() <= s.velMaxKmh());
            assertEquals(0.67, s.distanciaKm(), 0.02);
        }
    }

    @Test
    void descargaAisladaGeneraDecisionVisible() {
        List<GeoPoint> principal = List.of(new GeoPoint(0, 0), new GeoPoint(0, 0.004));
        List<GeoPoint> aislado = List.of(new GeoPoint(0.05, 0.05), new GeoPoint(0.05, 0.052));
        RoadGraph g = RoadGraph.construir(List.of(
                new RouteSegment(1, "PRINCIPAL", "#ff0000", principal),
                new RouteSegment(2, "AISLADO", "#0000ff", aislado)));
        var cargas = List.of(new SiteLocation(1, "CARGA 1", new GeoPoint(0, 0), null, "LOAD"));
        var descargas = List.of(
                new SiteLocation(10, "DESCARGA OK", new GeoPoint(0, 0.004), null, "DUMP"),
                new SiteLocation(11, "DESCARGA AISLADA", new GeoPoint(0.05, 0.05), null, "DUMP"));
        SimulationEngine e = new SimulationEngine(1,
                new SimConfig(2, 1, 15, 45, 1, 1, 200), g, cargas, descargas);

        assertTrue(e.snapshot().decisiones().stream()
                        .anyMatch(d -> d.contains("DESCARGA AISLADA")),
                "debe registrarse la decisión de excluir la descarga aislada");
        assertTrue(e.snapshot().camiones().stream()
                .allMatch(c -> "DESCARGA OK".equals(c.destino())));
    }
}
