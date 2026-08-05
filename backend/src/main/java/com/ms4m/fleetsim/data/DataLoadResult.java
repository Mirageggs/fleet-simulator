package com.ms4m.fleetsim.data;

import com.ms4m.fleetsim.model.RouteSegment;
import com.ms4m.fleetsim.model.SiteLocation;

import java.util.List;

/**
 * Resultado de cargar y validar el archivo de datos.
 * {@code operativo} indica si hay datos mínimos para simular
 * (al menos 1 tramo, 1 carga y 1 descarga válidos).
 */
public record DataLoadResult(
        List<RouteSegment> tramos,
        List<SiteLocation> cargas,
        List<SiteLocation> descargas,
        List<String> errores,
        List<String> advertencias,
        boolean operativo,
        String ordenCoordenadas) { }
