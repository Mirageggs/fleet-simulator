package com.ms4m.fleetsim.data;

import com.ms4m.fleetsim.config.AppProperties;
import com.ms4m.fleetsim.graph.RoadGraph;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Carga los datos al arrancar y construye el grafo vial.
 * Si el archivo falta o es ilegible, la aplicación arranca igual en estado
 * DEGRADADO (respuesta controlada), reportando el problema en /api/health y
 * /api/network, y rechazando iniciar simulaciones con un mensaje claro.
 */
@Service
public class NetworkDataService {

    private final DataLoadResult datos;
    private final RoadGraph grafo;

    public NetworkDataService(AppProperties props) {
        DataLoadResult resultado;
        try (InputStream in = abrir(props.getDataFile())) {
            resultado = new DataLoader().cargar(in, props.getCoordinateOrder());
        } catch (Exception e) {
            resultado = new DataLoadResult(List.of(), List.of(), List.of(),
                    List.of("No se pudo leer el archivo de datos (" + props.getDataFile() + "): " + e.getMessage()),
                    List.of(), false, "N/A");
        }
        this.datos = resultado;
        this.grafo = RoadGraph.construir(resultado.tramos());
    }

    private InputStream abrir(String ruta) throws IOException {
        if (ruta.startsWith("classpath:")) {
            InputStream in = getClass().getClassLoader()
                    .getResourceAsStream(ruta.substring("classpath:".length()));
            if (in == null) throw new FileNotFoundException("Recurso no encontrado: " + ruta);
            return in;
        }
        return Files.newInputStream(Path.of(ruta));
    }

    public DataLoadResult getDatos() { return datos; }
    public RoadGraph getGrafo() { return grafo; }
}
