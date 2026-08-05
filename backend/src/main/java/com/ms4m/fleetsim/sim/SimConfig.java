package com.ms4m.fleetsim.sim;

/** Parámetros de la simulación (independiente de Spring para poder probarse en aislamiento). */
public record SimConfig(
        int numCamiones,
        double tickSegundos,
        double velMinKmh,
        double velMaxKmh,
        int segundosCarga,
        int segundosDescarga,
        double snapMaxMetros) { }
