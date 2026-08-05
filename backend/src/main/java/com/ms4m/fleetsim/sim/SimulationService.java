package com.ms4m.fleetsim.sim;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ms4m.fleetsim.config.AppProperties;
import com.ms4m.fleetsim.data.NetworkDataService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Mantiene LA simulación activa en memoria (según el enunciado, el estado puede
 * vivir en memoria y debe poder reiniciarse: POST /api/simulation/start crea o
 * reinicia). Un hilo planificador avanza un tick por intervalo y difunde el
 * estado a los suscriptores SSE; si SSE no está disponible, el frontend usa
 * polling sobre GET /api/simulation.
 */
@Service
public class SimulationService {

    private final AppProperties props;
    private final NetworkDataService red;
    private final ObjectMapper json = new ObjectMapper();
    private final List<SseEmitter> emisores = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService planificador =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sim-tick");
                t.setDaemon(true);
                return t;
            });

    private volatile SimulationEngine engine;
    private volatile ScheduledFuture<?> tarea;

    public SimulationService(AppProperties props, NetworkDataService red) {
        this.props = props;
        this.red = red;
    }

    /** Crea (o reinicia) la simulación. Semilla opcional para reproducibilidad. */
    public synchronized SimulationEngine.SimView iniciar(Long semilla) {
        if (!red.getDatos().operativo()) {
            throw new IllegalStateException("Los datos cargados no permiten simular. Errores: "
                    + String.join(" | ", red.getDatos().errores()));
        }
        if (tarea != null) tarea.cancel(false);

        long s = semilla != null ? semilla : ThreadLocalRandom.current().nextLong(1, 1_000_000);
        engine = new SimulationEngine(s, config(), red.getGrafo(),
                red.getDatos().cargas(), red.getDatos().descargas());

        // Escala de tiempo: acelera SOLO el reloj de pared (el periodo entre ticks).
        // El motor sigue avanzando tickSeconds simulados por tick, así que las
        // muestras, promedios y el determinismo no cambian con la escala.
        double escala = Math.max(0.1, Math.min(50, props.getSim().getTimeScale()));
        long periodoMs = Math.max(100, Math.round(props.getSim().getTickSeconds() * 1000 / escala));
        tarea = planificador.scheduleAtFixedRate(this::tick, periodoMs, periodoMs, TimeUnit.MILLISECONDS);
        return engine.snapshot();
    }

    private void tick() {
        SimulationEngine actual = engine;
        if (actual == null) return;
        SimulationEngine.SimView vista = actual.avanzarTick();
        difundir("estado", vista);
        if (actual.finalizada()) {
            difundir("fin", vista);
            ScheduledFuture<?> t = tarea;
            if (t != null) t.cancel(false);
        }
    }

    private void difundir(String evento, Object dato) {
        String cuerpo;
        try {
            cuerpo = json.writeValueAsString(dato);
        } catch (Exception e) {
            return;
        }
        for (SseEmitter emisor : emisores) {
            try {
                emisor.send(SseEmitter.event().name(evento).data(cuerpo));
            } catch (Exception e) {
                emisores.remove(emisor);
            }
        }
    }

    /** Suscripción SSE; envía de inmediato el último estado conocido si existe. */
    public SseEmitter suscribir() {
        SseEmitter emisor = new SseEmitter(0L); // sin timeout
        emisores.add(emisor);
        emisor.onCompletion(() -> emisores.remove(emisor));
        emisor.onTimeout(() -> emisores.remove(emisor));
        emisor.onError(e -> emisores.remove(emisor));

        SimulationEngine actual = engine;
        if (actual != null) {
            try {
                emisor.send(SseEmitter.event().name("estado")
                        .data(json.writeValueAsString(actual.snapshot())));
            } catch (Exception ignored) {
                // el cliente reintentará; el planificador seguirá difundiendo
            }
        }
        return emisor;
    }

    public SimulationEngine getEngine() { return engine; }

    public SimConfig config() {
        AppProperties.Sim s = props.getSim();
        return new SimConfig(s.getTrucks(), s.getTickSeconds(), s.getSpeedMinKmh(),
                s.getSpeedMaxKmh(), s.getLoadingSeconds(), s.getUnloadingSeconds(),
                s.getSnapMaxMeters());
    }
}
