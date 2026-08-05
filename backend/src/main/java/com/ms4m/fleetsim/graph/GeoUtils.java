package com.ms4m.fleetsim.graph;

import com.ms4m.fleetsim.model.GeoPoint;

/** Utilidades geográficas puras (sin dependencias de Spring). */
public final class GeoUtils {

    public static final double RADIO_TIERRA_M = 6_371_000.0;

    private GeoUtils() { }

    /** Distancia haversine en metros entre dos coordenadas. */
    public static double haversineM(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * RADIO_TIERRA_M * Math.asin(Math.min(1.0, Math.sqrt(s)));
    }

    /** Interpolación lineal entre dos puntos (suficiente a escala de una mina). */
    public static GeoPoint interpolar(GeoPoint a, GeoPoint b, double t) {
        return new GeoPoint(a.lat() + (b.lat() - a.lat()) * t,
                a.lng() + (b.lng() - a.lng()) * t);
    }
}
