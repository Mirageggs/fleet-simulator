package com.ms4m.fleetsim.model;

import java.util.List;

/** Tramo vial validado: polilínea de al menos 2 puntos. */
public record RouteSegment(long id, String nombre, String color, List<GeoPoint> puntos) { }
