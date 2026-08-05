package com.ms4m.fleetsim.report;

import com.ms4m.fleetsim.sim.SimulationEngine.CamionStats;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Explicación del reporte en lenguaje humano mediante reglas deterministas.
 * Es el camino por defecto y también el respaldo cuando el LLM opcional no
 * está disponible. Umbrales configurables por properties/variables de entorno.
 */
public class HeuristicExplainer {

    public String explicar(List<CamionStats> stats, boolean parcial,
                           double umbralRapidoPct, double umbralLentoPct, int minMuestras) {
        StringBuilder sb = new StringBuilder();
        if (parcial) {
            sb.append("Reporte parcial: la simulación sigue en curso y las cifras pueden cambiar.\n");
        }

        List<CamionStats> conDatos = stats.stream().filter(s -> s.muestras() > 0).toList();
        if (conDatos.isEmpty()) {
            sb.append("Aún no hay muestras de velocidad registradas: los camiones no han iniciado su trayecto.");
            return sb.toString();
        }

        double promedioFlota = conDatos.stream()
                .mapToDouble(CamionStats::velPromedioKmh).average().orElse(0);
        sb.append(f("La flota registró una velocidad promedio de %.1f km/h entre %d camiones con datos.",
                promedioFlota, conDatos.size()));

        CamionStats masRapido = conDatos.stream()
                .max(Comparator.comparingDouble(CamionStats::velPromedioKmh)).orElseThrow();
        CamionStats masLento = conDatos.stream()
                .min(Comparator.comparingDouble(CamionStats::velPromedioKmh)).orElseThrow();
        if (conDatos.size() > 1 && !masRapido.id().equals(masLento.id())) {
            sb.append('\n').append(f(
                    "El más rápido fue %s con %.1f km/h (%+.0f%% frente a la flota) y el más lento %s con %.1f km/h (%+.0f%%).",
                    masRapido.id(), masRapido.velPromedioKmh(), desvio(masRapido, promedioFlota),
                    masLento.id(), masLento.velPromedioKmh(), desvio(masLento, promedioFlota)));
        }

        boolean algunoDestacado = false;
        for (CamionStats c : conDatos) {
            double desvio = desvio(c, promedioFlota);
            if (desvio >= umbralRapidoPct) {
                algunoDestacado = true;
                sb.append('\n').append(f(
                        "%s superó el promedio de la flota en %.0f%% (ruta %s → %s), un ciclo notablemente ágil.",
                        c.id(), desvio, c.origen(), c.destino()));
            } else if (desvio <= -umbralLentoPct) {
                algunoDestacado = true;
                sb.append('\n').append(f(
                        "%s quedó %.0f%% por debajo del promedio (ruta %s → %s); en una operación real convendría revisar la pendiente del tramo, la carga o el equipo.",
                        c.id(), -desvio, c.origen(), c.destino()));
            }
        }
        if (!algunoDestacado && conDatos.size() > 1) {
            sb.append('\n').append(f(
                    "Todos los camiones se mantuvieron dentro de ±%.0f%% del promedio: desempeño homogéneo de la flota.",
                    Math.max(umbralRapidoPct, umbralLentoPct)));
        }

        for (CamionStats c : conDatos) {
            if (c.muestras() < minMuestras) {
                sb.append('\n').append(f(
                        "Advertencia: %s registró solo %d muestras (mínimo confiable: %d); su estadística es poco representativa.",
                        c.id(), c.muestras(), minMuestras));
            }
        }
        for (CamionStats c : stats) {
            if ("SIN_RUTA".equals(c.estado())) {
                sb.append('\n').append(c.id())
                        .append(" no obtuvo una ruta conectada y no participó del recorrido.");
            }
        }
        return sb.toString();
    }

    private double desvio(CamionStats c, double promedioFlota) {
        return promedioFlota <= 0 ? 0 : 100.0 * (c.velPromedioKmh() / promedioFlota - 1);
    }

    /** Formato con Locale.ROOT para que los decimales usen punto sin importar el servidor. */
    private String f(String plantilla, Object... args) {
        return String.format(Locale.ROOT, plantilla, args);
    }
}
