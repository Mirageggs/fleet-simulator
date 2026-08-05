package com.ms4m.fleetsim.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ms4m.fleetsim.config.AppProperties;
import com.ms4m.fleetsim.sim.SimulationEngine;
import com.ms4m.fleetsim.sim.SimulationEngine.CamionStats;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Construye el reporte de velocidades: media aritmética de muestras tomadas a
 * intervalos uniformes por camión, resumen de flota y explicación en lenguaje
 * humano (heurística determinista; LLM opcional con respaldo).
 * Puede generarse con la simulación en curso (reporte parcial) o al finalizar.
 */
@Service
public class ReportService {

    public record Explicacion(String texto, String fuente) { }

    public record Flota(int camionesConDatos, double promedioKmh, String masRapido, String masLento) { }

    public record Reporte(String generadoEn, boolean parcial, long semilla, double tickSegundos,
                          List<CamionStats> camiones, Flota flota, Explicacion explicacion,
                          List<String> decisiones) { }

    private final AppProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final HeuristicExplainer heuristica = new HeuristicExplainer();

    public ReportService(AppProperties props) {
        this.props = props;
    }

    public Reporte generar(SimulationEngine engine) {
        if (engine == null) {
            throw new NoSuchElementException(
                    "Aún no se ha ejecutado ninguna simulación. Inicia una con POST /api/simulation/start.");
        }

        List<CamionStats> stats = engine.estadisticas();
        boolean parcial = !engine.finalizada();

        List<CamionStats> conDatos = stats.stream().filter(s -> s.muestras() > 0).toList();
        double promedio = conDatos.stream().mapToDouble(CamionStats::velPromedioKmh).average().orElse(0);
        String masRapido = conDatos.stream()
                .max(Comparator.comparingDouble(CamionStats::velPromedioKmh))
                .map(CamionStats::id).orElse("-");
        String masLento = conDatos.stream()
                .min(Comparator.comparingDouble(CamionStats::velPromedioKmh))
                .map(CamionStats::id).orElse("-");
        Flota flota = new Flota(conDatos.size(), Math.round(promedio * 100.0) / 100.0, masRapido, masLento);

        AppProperties.Report cfg = props.getReport();
        String textoHeuristico = heuristica.explicar(stats, parcial,
                cfg.getFastThresholdPct(), cfg.getSlowThresholdPct(), cfg.getMinSamples());

        String texto = textoHeuristico;
        String fuente = "heurística";
        AppProperties.Llm llm = cfg.getLlm();
        if (llm.isEnabled()) {
            if (llm.getApiKey() == null || llm.getApiKey().isBlank()) {
                fuente = "heurística (LLM habilitado sin ANTHROPIC_API_KEY; se usó el respaldo)";
            } else {
                Optional<String> textoLlm = new LlmExplainer(llm.getModel(), llm.getApiKey(), json)
                        .explicar(estadisticasComoJson(parcial, stats, flota));
                if (textoLlm.isPresent()) {
                    texto = textoLlm.get();
                    fuente = "llm (" + llm.getModel() + ")";
                } else {
                    fuente = "heurística (LLM no disponible; se usó el respaldo)";
                }
            }
        }

        return new Reporte(OffsetDateTime.now().toString(), parcial, engine.semilla(),
                props.getSim().getTickSeconds(), stats, flota,
                new Explicacion(texto, fuente), engine.snapshot().decisiones());
    }

    private String estadisticasComoJson(boolean parcial, List<CamionStats> stats, Flota flota) {
        try {
            return json.writeValueAsString(Map.of("parcial", parcial, "camiones", stats, "flota", flota));
        } catch (Exception e) {
            return "{}";
        }
    }
}
