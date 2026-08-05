package com.ms4m.fleetsim.report;

import com.ms4m.fleetsim.sim.SimulationEngine.CamionStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicExplainerTest {

    private CamionStats stats(String id, int muestras, double promedio) {
        return new CamionStats(id, "FINALIZADO", "CARGA A", "DESCARGA B",
                muestras, promedio - 3, promedio + 3, promedio, 1.5, muestras);
    }

    @Test
    void identificaRapidoLentoYAdvertencias() {
        List<CamionStats> lista = List.of(
                stats("CAM-001", 50, 40.0),   // muy por encima
                stats("CAM-002", 50, 30.0),
                stats("CAM-003", 5, 20.0));   // lento y con pocas muestras
        String texto = new HeuristicExplainer().explicar(lista, false, 10, 10, 10);

        assertTrue(texto.contains("CAM-001"), "debe mencionar al más rápido");
        assertTrue(texto.contains("CAM-003"), "debe mencionar al más lento");
        assertTrue(texto.contains("por debajo"), "debe describir el rezago");
        assertTrue(texto.contains("muestras"), "debe advertir sobre pocas muestras");
    }

    @Test
    void reporteParcialLoIndica() {
        String texto = new HeuristicExplainer()
                .explicar(List.of(stats("CAM-001", 20, 30.0)), true, 10, 10, 10);
        assertTrue(texto.toLowerCase().contains("parcial"));
    }

    @Test
    void sinMuestrasRespondeControladamente() {
        String texto = new HeuristicExplainer()
                .explicar(List.of(stats("CAM-001", 0, 0)), false, 10, 10, 10);
        assertTrue(texto.contains("no hay muestras") || texto.contains("Aún"));
    }
}
