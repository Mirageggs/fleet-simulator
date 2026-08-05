package com.ms4m.fleetsim.graph;

import com.ms4m.fleetsim.model.GeoPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoUtilsTest {

    @Test
    void haversineDeUnGradoDeLongitudEnElEcuador() {
        double d = GeoUtils.haversineM(new GeoPoint(0, 0), new GeoPoint(0, 1));
        // 1° de longitud en el ecuador ≈ 111.19 km
        assertEquals(111_195, d, 250);
    }

    @Test
    void haversineDePuntosIgualesEsCero() {
        GeoPoint p = new GeoPoint(-9.53, -77.06);
        assertEquals(0, GeoUtils.haversineM(p, p), 1e-6);
    }

    @Test
    void interpolarEnLaMitadDelSegmento() {
        GeoPoint m = GeoUtils.interpolar(new GeoPoint(0, 0), new GeoPoint(2, 4), 0.5);
        assertEquals(1, m.lat(), 1e-9);
        assertEquals(2, m.lng(), 1e-9);
    }
}
