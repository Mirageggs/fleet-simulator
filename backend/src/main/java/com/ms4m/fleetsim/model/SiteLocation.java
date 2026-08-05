package com.ms4m.fleetsim.model;

/**
 * Ubicación de carga (Load) o descarga (Dump).
 * El radio puede ser null: el archivo de entrada lo permite.
 */
public record SiteLocation(long id, String nombre, GeoPoint punto, Double radio, String tipo) { }
