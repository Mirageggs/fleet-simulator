package com.ms4m.fleetsim.data;

import com.ms4m.fleetsim.graph.RoadGraph;
import com.ms4m.fleetsim.sim.SimConfig;
import com.ms4m.fleetsim.sim.SimulationEngine;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de regresión sobre el dataset REAL de la evaluación
 * (backend/src/main/resources/data/data.json = data-prueba.json).
 *
 * Documenta y fija lo que el sistema encuentra en esos datos:
 *  - 553 tramos, 30 cargas y 139 descargas, todos válidos (orden [lat, lng]).
 *  - Grafo de 4 componentes conexos.
 *  - Una carga (MJ392-C1) y una descarga (BOT-816-DES_BOXCUT_MJS) quedan en
 *    componentes sin contraparte: el motor las excluye con decisión visible.
 *  - La simulación sobre estos datos es determinista.
 */
class RealDatasetTest {

    private DataLoadResult cargarDatosReales() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("data/data.json");
        assertNotNull(in, "debe existir data/data.json en resources");
        return new DataLoader().cargar(in, "AUTO");
    }

    @Test
    void cargaYValidaElDatasetRealCompleto() {
        DataLoadResult d = cargarDatosReales();

        assertTrue(d.operativo());
        assertEquals("LAT_LNG", d.ordenCoordenadas());
        assertEquals(553, d.tramos().size());
        assertEquals(30, d.cargas().size());
        assertEquals(139, d.descargas().size());
        assertTrue(d.errores().isEmpty(), "el dataset real no tiene registros inválidos");
        long radiosNulos = d.descargas().stream().filter(u -> u.radio() == null).count();
        assertEquals(8, radiosNulos, "el dataset real trae 8 descargas con radio null");
    }

    @Test
    void construyeElGrafoConCuatroComponentes() {
        DataLoadResult d = cargarDatosReales();
        RoadGraph g = RoadGraph.construir(d.tramos());

        assertEquals(16_497, g.numNodos());
        assertEquals(4, g.numComponentes());
    }

    @Test
    void excluyeUbicacionesVaradasConDecisionVisibleYEsDeterminista() {
        DataLoadResult d = cargarDatosReales();
        RoadGraph g = RoadGraph.construir(d.tramos());
        SimConfig cfg = new SimConfig(5, 1, 15, 45, 4, 4, 200);

        SimulationEngine e1 = new SimulationEngine(42, cfg, g, d.cargas(), d.descargas());
        SimulationEngine e2 = new SimulationEngine(42, cfg, g, d.cargas(), d.descargas());

        var decisiones = e1.snapshot().decisiones();
        assertTrue(decisiones.stream().anyMatch(x -> x.contains("MJ392-C1")),
                "debe excluirse la carga varada con decisión visible");
        assertTrue(decisiones.stream().anyMatch(x -> x.contains("BOT-816-DES_BOXCUT_MJS")),
                "debe excluirse la descarga varada con decisión visible");

        for (int i = 0; i < 60; i++) {
            assertEquals(e1.avanzarTick(), e2.avanzarTick(), "divergencia en el tick " + (i + 1));
        }
        assertTrue(e1.snapshot().camiones().stream()
                        .noneMatch(c -> "SIN_RUTA".equals(c.estado())),
                "los 5 camiones deben obtener ruta en el dataset real");
    }
}
