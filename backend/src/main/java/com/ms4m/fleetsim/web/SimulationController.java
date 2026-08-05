package com.ms4m.fleetsim.web;

import com.ms4m.fleetsim.report.ReportService;
import com.ms4m.fleetsim.sim.SimulationEngine;
import com.ms4m.fleetsim.sim.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/** Ciclo de vida de la simulación: iniciar/reiniciar, estado, stream SSE y reporte. */
@Tag(name = "Simulación")
@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    public record StartRequest(Long semilla) { }

    private final SimulationService simulacion;
    private final ReportService reportes;

    public SimulationController(SimulationService simulacion, ReportService reportes) {
        this.simulacion = simulacion;
        this.reportes = reportes;
    }

    @Operation(summary = "Inicia una simulación nueva (o reinicia la actual). Semilla opcional para reproducir corridas")
    @PostMapping("/start")
    public SimulationEngine.SimView iniciar(@RequestBody(required = false) StartRequest peticion) {
        return simulacion.iniciar(peticion == null ? null : peticion.semilla());
    }

    @Operation(summary = "Estado actual de la simulación (fallback de polling si SSE no está disponible)")
    @GetMapping
    public ResponseEntity<Object> estado() {
        SimulationEngine engine = simulacion.getEngine();
        if (engine == null) {
            return ResponseEntity.ok(Map.of("estado", "SIN_SIMULACION"));
        }
        return ResponseEntity.ok(engine.snapshot());
    }

    @Operation(summary = "Stream SSE en tiempo real: evento 'estado' por tick y evento 'fin' al terminar")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return simulacion.suscribir();
    }

    @Operation(summary = "Reporte de velocidad promedio por camión con explicación en lenguaje humano (parcial si sigue en curso)")
    @GetMapping("/report")
    public ReportService.Reporte reporte() {
        return reportes.generar(simulacion.getEngine());
    }
}
