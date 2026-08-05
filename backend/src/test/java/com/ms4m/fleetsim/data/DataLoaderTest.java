package com.ms4m.fleetsim.data;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataLoaderTest {

    private DataLoadResult cargar(String json, String orden) {
        InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        return new DataLoader().cargar(in, orden);
    }

    @Test
    void descartaRegistrosInvalidosSinRomperse() {
        String json = """
                {
                  "Routes": [
                    {"id_trm_cs": 1, "nombre_tramo": "TRONCAL", "color": "#FF0000",
                     "points": [[-9.53, -77.06], [-9.531, -77.061], [-9.532, -77.062]]},
                    {"id_trm_cs": 2, "nombre_tramo": "DEFECTUOSO", "color": "#000000",
                     "points": [[-9.53, -77.06]]}
                  ],
                  "Load": [
                    {"id": 1, "name": "CARGA A", "coor": [-9.53, -77.06], "radio": 20}
                  ],
                  "Dump": [
                    {"id": 10, "name": "DESCARGA B", "coor": [-9.532, -77.062], "radio": null},
                    {"id": 99, "name": "SIN COORDENADA", "coor": null, "radio": 15}
                  ]
                }
                """;
        DataLoadResult r = cargar(json, "AUTO");

        assertTrue(r.operativo());
        assertEquals(1, r.tramos().size(), "el tramo de 1 punto debe descartarse");
        assertEquals(1, r.cargas().size());
        assertEquals(1, r.descargas().size(), "la descarga sin coordenada debe descartarse");
        assertNull(r.descargas().get(0).radio(), "radio null es válido y debe conservarse");
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("DEFECTUOSO")));
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("SIN COORDENADA")));
        assertEquals("LAT_LNG", r.ordenCoordenadas());
    }

    @Test
    void detectaOrdenLngLatAutomaticamente() {
        String json = """
                {
                  "Routes": [
                    {"id_trm_cs": 1, "nombre_tramo": "T", "color": "#00FF00",
                     "points": [[-77.06, -9.53], [-77.061, -9.531]]}
                  ],
                  "Load": [{"id": 1, "name": "A", "coor": [-77.06, -9.53], "radio": 5}],
                  "Dump": [{"id": 2, "name": "B", "coor": [-77.061, -9.531], "radio": 5}]
                }
                """;
        DataLoadResult r = cargar(json, "AUTO");

        assertEquals("LNG_LAT", r.ordenCoordenadas());
        // La latitud reconstruida debe quedar en el rango peruano, no la longitud
        assertEquals(-9.53, r.cargas().get(0).punto().lat(), 1e-9);
        assertEquals(-77.06, r.cargas().get(0).punto().lng(), 1e-9);
    }

    @Test
    void jsonInvalidoRespondeControladamente() {
        DataLoadResult r = cargar("esto no es json {", "AUTO");
        assertFalse(r.operativo());
        assertFalse(r.errores().isEmpty());
        assertTrue(r.tramos().isEmpty());
    }

    @Test
    void faltanColeccionesRespondeControladamente() {
        DataLoadResult r = cargar("{\"otraCosa\": []}", "AUTO");
        assertFalse(r.operativo());
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("Routes")));
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("Load")));
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("Dump")));
    }
}
