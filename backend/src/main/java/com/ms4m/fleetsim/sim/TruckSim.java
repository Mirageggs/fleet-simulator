package com.ms4m.fleetsim.sim;

import com.ms4m.fleetsim.graph.GeoUtils;
import com.ms4m.fleetsim.model.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Estado de un camión durante la simulación.
 *
 * Modelo de velocidad: cada camión recibe una velocidad base propia (uniforme
 * en [min, max], derivada de la semilla) y en cada tick varía ±15% del rango
 * alrededor de esa base, siempre acotada a [min, max]. Así las velocidades
 * cambian durante el viaje y, además, los promedios difieren entre camiones,
 * lo que hace informativo el reporte comparativo.
 *
 * Las muestras de velocidad se registran a intervalos uniformes (una por tick)
 * únicamente mientras el camión está EN_RUTA.
 */
class TruckSim {

    enum Estado { CARGANDO, EN_RUTA, DESCARGANDO, FINALIZADO, SIN_RUTA }

    final String id;
    final String origen;
    final String destino;
    final List<GeoPoint> camino;
    final double[] acumuladoM;
    final double distanciaTotalM;
    final List<Double> muestras = new ArrayList<>();

    private final Random rng;
    private final double velBaseKmh;

    Estado estado;
    double recorridoM = 0;
    double velActualKmh = 0;
    private double esperaSeg;

    TruckSim(String id, String origen, String destino, List<GeoPoint> camino, Random rng, SimConfig cfg) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.camino = camino;
        this.rng = rng;

        if (camino == null || camino.size() < 2) {
            this.acumuladoM = new double[]{0};
            this.distanciaTotalM = 0;
            this.velBaseKmh = 0;
            this.esperaSeg = 0;
            this.estado = Estado.SIN_RUTA;
            return;
        }
        this.acumuladoM = new double[camino.size()];
        double acumulado = 0;
        for (int i = 1; i < camino.size(); i++) {
            acumulado += GeoUtils.haversineM(camino.get(i - 1), camino.get(i));
            acumuladoM[i] = acumulado;
        }
        this.distanciaTotalM = acumulado;
        this.velBaseKmh = cfg.velMinKmh() + rng.nextDouble() * (cfg.velMaxKmh() - cfg.velMinKmh());
        this.esperaSeg = cfg.segundosCarga();
        this.estado = cfg.segundosCarga() > 0 ? Estado.CARGANDO : Estado.EN_RUTA;
    }

    /** Avanza un tick de simulación. */
    void avanzar(SimConfig cfg) {
        double dt = cfg.tickSegundos();
        switch (estado) {
            case CARGANDO -> {
                esperaSeg -= dt;
                if (esperaSeg <= 0) estado = Estado.EN_RUTA;
            }
            case EN_RUTA -> {
                double variacion = (rng.nextDouble() * 2 - 1) * 0.15 * (cfg.velMaxKmh() - cfg.velMinKmh());
                velActualKmh = acotar(velBaseKmh + variacion, cfg.velMinKmh(), cfg.velMaxKmh());
                muestras.add(velActualKmh);
                recorridoM += velActualKmh / 3.6 * dt;
                if (recorridoM >= distanciaTotalM) {
                    recorridoM = distanciaTotalM;
                    velActualKmh = 0;
                    esperaSeg = cfg.segundosDescarga();
                    estado = cfg.segundosDescarga() > 0 ? Estado.DESCARGANDO : Estado.FINALIZADO;
                }
            }
            case DESCARGANDO -> {
                esperaSeg -= dt;
                if (esperaSeg <= 0) estado = Estado.FINALIZADO;
            }
            default -> { /* FINALIZADO o SIN_RUTA: nada que hacer */ }
        }
    }

    /** Posición actual interpolada sobre la polilínea del camino. */
    GeoPoint posicion() {
        if (camino == null || camino.isEmpty()) return null;
        if (camino.size() < 2 || recorridoM <= 0) return camino.get(0);
        if (recorridoM >= distanciaTotalM) return camino.get(camino.size() - 1);
        int i = 1;
        while (i < acumuladoM.length && acumuladoM[i] < recorridoM) i++;
        double inicioSegmento = acumuladoM[i - 1];
        double largoSegmento = acumuladoM[i] - inicioSegmento;
        double t = largoSegmento <= 0 ? 0 : (recorridoM - inicioSegmento) / largoSegmento;
        return GeoUtils.interpolar(camino.get(i - 1), camino.get(i), t);
    }

    boolean terminado() {
        return estado == Estado.FINALIZADO || estado == Estado.SIN_RUTA;
    }

    private static double acotar(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
