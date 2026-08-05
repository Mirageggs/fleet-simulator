package com.ms4m.fleetsim.web;

import com.ms4m.fleetsim.config.AppProperties;
import com.ms4m.fleetsim.data.DataLoadResult;
import com.ms4m.fleetsim.data.NetworkDataService;
import com.ms4m.fleetsim.model.SiteLocation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Endpoints de la red vial cargada y del estado de salud del servicio. */
@Tag(name = "Red vial")
@RestController
@RequestMapping("/api")
public class NetworkController {

    public record TramoDto(long id, String nombre, String color, List<double[]> puntos) { }

    public record UbicacionDto(long id, String nombre, double lat, double lng, Double radio) { }

    public record GrafoDto(int nodos, int aristas, int componentesConexos) { }

    public record ConfigDto(double velMinKmh, double velMaxKmh, double tickSegundos,
                            double escalaTiempo, int camiones) { }

    public record ValidacionDto(boolean operativo, String ordenCoordenadas,
                                List<String> errores, List<String> advertencias) { }

    public record RedDto(List<TramoDto> tramos, List<UbicacionDto> cargas, List<UbicacionDto> descargas,
                         GrafoDto grafo, ConfigDto config, ValidacionDto validacion) { }

    private final NetworkDataService datos;
    private final AppProperties props;

    public NetworkController(NetworkDataService datos, AppProperties props) {
        this.datos = datos;
        this.props = props;
    }

    @Operation(summary = "Red vial completa: tramos, cargas, descargas, grafo, configuración y validación de datos")
    @GetMapping("/network")
    public RedDto red() {
        DataLoadResult d = datos.getDatos();
        List<TramoDto> tramos = d.tramos().stream()
                .map(t -> new TramoDto(t.id(), t.nombre(), t.color(),
                        t.puntos().stream().map(p -> new double[]{p.lat(), p.lng()}).toList()))
                .toList();
        AppProperties.Sim s = props.getSim();
        return new RedDto(tramos, aDto(d.cargas()), aDto(d.descargas()),
                new GrafoDto(datos.getGrafo().numNodos(), datos.getGrafo().numAristas(),
                        datos.getGrafo().numComponentes()),
                new ConfigDto(s.getSpeedMinKmh(), s.getSpeedMaxKmh(), s.getTickSeconds(),
                        s.getTimeScale(), s.getTrucks()),
                new ValidacionDto(d.operativo(), d.ordenCoordenadas(), d.errores(), d.advertencias()));
    }

    @Operation(summary = "Salud del servicio y resumen de la carga de datos")
    @GetMapping("/health")
    public Map<String, Object> health() {
        DataLoadResult d = datos.getDatos();
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("estado", d.operativo() ? "OK" : "DEGRADADO");
        salida.put("tramosValidos", d.tramos().size());
        salida.put("cargas", d.cargas().size());
        salida.put("descargas", d.descargas().size());
        salida.put("componentesConexos", datos.getGrafo().numComponentes());
        salida.put("errores", d.errores().size());
        salida.put("advertencias", d.advertencias().size());
        return salida;
    }

    private List<UbicacionDto> aDto(List<SiteLocation> ubicaciones) {
        return ubicaciones.stream()
                .map(u -> new UbicacionDto(u.id(), u.nombre(), u.punto().lat(), u.punto().lng(), u.radio()))
                .toList();
    }
}
