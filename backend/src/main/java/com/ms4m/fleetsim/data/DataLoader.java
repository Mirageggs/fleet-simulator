package com.ms4m.fleetsim.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ms4m.fleetsim.model.GeoPoint;
import com.ms4m.fleetsim.model.RouteSegment;
import com.ms4m.fleetsim.model.SiteLocation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee y valida el archivo de datos (Routes / Load / Dump).
 *
 * Principios:
 *  - NUNCA modifica el archivo de entrada.
 *  - Los registros inválidos se descartan y quedan reportados en
 *    {@code errores} (impiden usar el registro) o {@code advertencias}
 *    (se corrigió con un valor por defecto).
 *  - Tolera variantes de claves (Routes/routes, Load/loads, etc.) y detecta
 *    automáticamente el orden de coordenadas [lat,lng] vs [lng,lat].
 */
public class DataLoader {

    private static final String[] CLAVES_TRAMOS = {"Routes", "routes", "Tramos", "tramos"};
    private static final String[] CLAVES_CARGA = {"Load", "load", "Loads", "loads", "Cargas", "cargas"};
    private static final String[] CLAVES_DESCARGA = {"Dump", "dump", "Dumps", "dumps", "Descargas", "descargas"};
    private static final String[] CLAVES_PUNTOS = {"points", "puntos", "coords"};
    private static final String[] CLAVES_COOR = {"coor", "coord", "coordenadas", "punto"};

    private final ObjectMapper mapper = new ObjectMapper();

    public DataLoadResult cargar(InputStream entrada, String ordenConfigurado) {
        List<String> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();

        JsonNode raiz;
        try {
            raiz = mapper.readTree(entrada);
        } catch (Exception e) {
            errores.add("El archivo no es un JSON válido: " + e.getMessage());
            return vacio(errores, advertencias);
        }
        if (raiz == null || !raiz.isObject()) {
            errores.add("La raíz del JSON debe ser un objeto con las colecciones Routes, Load y Dump.");
            return vacio(errores, advertencias);
        }

        JsonNode nTramos = primeraLista(raiz, CLAVES_TRAMOS);
        JsonNode nCargas = primeraLista(raiz, CLAVES_CARGA);
        JsonNode nDescargas = primeraLista(raiz, CLAVES_DESCARGA);
        if (nTramos == null) errores.add("No se encontró la colección de tramos (clave esperada: \"Routes\").");
        if (nCargas == null) errores.add("No se encontró la colección de cargas (clave esperada: \"Load\").");
        if (nDescargas == null) errores.add("No se encontró la colección de descargas (clave esperada: \"Dump\").");

        boolean latPrimero = decidirOrden(recolectarPares(nTramos, nCargas, nDescargas),
                ordenConfigurado, advertencias);
        String orden = latPrimero ? "LAT_LNG" : "LNG_LAT";

        List<RouteSegment> tramos = parsearTramos(nTramos, latPrimero, errores, advertencias);
        List<SiteLocation> cargas = parsearUbicaciones(nCargas, "carga", "LOAD", latPrimero, errores, advertencias);
        List<SiteLocation> descargas = parsearUbicaciones(nDescargas, "descarga", "DUMP", latPrimero, errores, advertencias);

        if (nTramos != null && tramos.isEmpty())
            errores.add("Ningún tramo resultó válido: la simulación no puede ejecutarse.");
        if (nCargas != null && cargas.isEmpty())
            errores.add("Ninguna ubicación de carga resultó válida: la simulación no puede ejecutarse.");
        if (nDescargas != null && descargas.isEmpty())
            errores.add("Ninguna ubicación de descarga resultó válida: la simulación no puede ejecutarse.");

        boolean operativo = !tramos.isEmpty() && !cargas.isEmpty() && !descargas.isEmpty();
        return new DataLoadResult(List.copyOf(tramos), List.copyOf(cargas), List.copyOf(descargas),
                List.copyOf(errores), List.copyOf(advertencias), operativo, orden);
    }

    // ------------------------------------------------------------------ tramos

    private List<RouteSegment> parsearTramos(JsonNode lista, boolean latPrimero,
                                             List<String> errores, List<String> advertencias) {
        List<RouteSegment> tramos = new ArrayList<>();
        if (lista == null) return tramos;
        int i = 0;
        for (JsonNode t : lista) {
            i++;
            long id = t.path("id_trm_cs").isNumber() ? t.path("id_trm_cs").asLong()
                    : (t.path("id").isNumber() ? t.path("id").asLong() : -i);
            String nombre = texto(t, "nombre_tramo", "nombre", "name");
            if (nombre == null) {
                nombre = "TRAMO " + (id > 0 ? id : "#" + i);
                advertencias.add("Tramo #" + i + " sin nombre; se usa \"" + nombre + "\".");
            }
            String color = texto(t, "color");
            if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
                advertencias.add("Tramo \"" + nombre + "\" con color ausente o inválido; se usa gris por defecto.");
                color = "#607D8B";
            }
            List<GeoPoint> puntos = new ArrayList<>();
            int invalidos = 0;
            JsonNode pts = primero(t, CLAVES_PUNTOS);
            if (pts != null && pts.isArray()) {
                for (JsonNode p : pts) {
                    GeoPoint gp = leerPar(p, latPrimero);
                    if (gp == null) invalidos++;
                    else puntos.add(gp);
                }
            }
            if (invalidos > 0)
                advertencias.add("Tramo \"" + nombre + "\": se descartaron " + invalidos + " punto(s) inválido(s).");
            if (puntos.size() < 2) {
                errores.add("Tramo \"" + nombre + "\" descartado: necesita al menos 2 puntos válidos y tiene "
                        + puntos.size() + ".");
                continue;
            }
            tramos.add(new RouteSegment(id, nombre, color, List.copyOf(puntos)));
        }
        return tramos;
    }

    // ------------------------------------------------------------- ubicaciones

    private List<SiteLocation> parsearUbicaciones(JsonNode lista, String etiqueta, String tipo,
                                                  boolean latPrimero, List<String> errores,
                                                  List<String> advertencias) {
        List<SiteLocation> ubicaciones = new ArrayList<>();
        if (lista == null) return ubicaciones;
        int i = 0;
        for (JsonNode n : lista) {
            i++;
            long id = n.path("id").isNumber() ? n.path("id").asLong() : -i;
            String nombre = texto(n, "name", "nombre");
            if (nombre == null) {
                nombre = etiqueta.toUpperCase() + " " + (id > 0 ? id : "#" + i);
                advertencias.add("Ubicación de " + etiqueta + " #" + i + " sin nombre; se usa \"" + nombre + "\".");
            }
            GeoPoint punto = leerPar(primero(n, CLAVES_COOR), latPrimero);
            if (punto == null) {
                errores.add("Ubicación de " + etiqueta + " \"" + nombre
                        + "\" descartada: coordenada ausente o inválida.");
                continue;
            }
            JsonNode r = n.path("radio");
            Double radio = r.isNumber() ? r.asDouble() : null; // null es válido según el enunciado
            if (!r.isMissingNode() && !r.isNull() && !r.isNumber())
                advertencias.add("Ubicación \"" + nombre + "\": radio no numérico; se trata como null.");
            ubicaciones.add(new SiteLocation(id, nombre, punto, radio, tipo));
        }
        return ubicaciones;
    }

    // ------------------------------------------------- orden de coordenadas

    /**
     * AUTO: si algún |primer valor| &gt; 90 no puede ser latitud =&gt; [lng,lat].
     * Si no decide por rango, compara magnitudes medias (en Perú |lng|≈77 y
     * |lat|≈9-13, la diferencia es inequívoca). En empate asume [lat,lng].
     */
    private boolean decidirOrden(List<double[]> pares, String cfg, List<String> advertencias) {
        if ("LAT_LNG".equalsIgnoreCase(cfg)) return true;
        if ("LNG_LAT".equalsIgnoreCase(cfg)) return false;
        if (pares.isEmpty()) return true;

        double maxA = 0, maxB = 0, sumA = 0, sumB = 0;
        for (double[] p : pares) {
            maxA = Math.max(maxA, Math.abs(p[0]));
            maxB = Math.max(maxB, Math.abs(p[1]));
            sumA += Math.abs(p[0]);
            sumB += Math.abs(p[1]);
        }
        double mediaA = sumA / pares.size();
        double mediaB = sumB / pares.size();

        if (maxA > 90 && maxB <= 90) {
            advertencias.add("Orden de coordenadas detectado automáticamente: [lng, lat].");
            return false;
        }
        if (maxB > 90 && maxA <= 90) return true;
        if (mediaA > mediaB + 20) {
            advertencias.add("Orden de coordenadas detectado automáticamente: [lng, lat].");
            return false;
        }
        if (mediaB > mediaA + 20) return true;

        advertencias.add("Orden de coordenadas ambiguo; se asume [lat, lng] (configurable con APP_COORD_ORDER).");
        return true;
    }

    private List<double[]> recolectarPares(JsonNode... listas) {
        List<double[]> pares = new ArrayList<>();
        for (JsonNode lista : listas) {
            if (lista == null) continue;
            for (JsonNode item : lista) {
                JsonNode pts = primero(item, CLAVES_PUNTOS);
                if (pts != null && pts.isArray()) {
                    for (JsonNode p : pts) agregarPar(p, pares);
                }
                agregarPar(primero(item, CLAVES_COOR), pares);
            }
        }
        return pares;
    }

    private void agregarPar(JsonNode p, List<double[]> destino) {
        if (p != null && p.isArray() && p.size() >= 2
                && p.get(0).isNumber() && p.get(1).isNumber()) {
            destino.add(new double[]{p.get(0).asDouble(), p.get(1).asDouble()});
        }
    }

    /** Lee un par [a, b] y lo valida contra rangos geográficos. Devuelve null si es inválido. */
    private GeoPoint leerPar(JsonNode p, boolean latPrimero) {
        if (p == null || !p.isArray() || p.size() < 2
                || !p.get(0).isNumber() || !p.get(1).isNumber()) return null;
        double a = p.get(0).asDouble();
        double b = p.get(1).asDouble();
        double lat = latPrimero ? a : b;
        double lng = latPrimero ? b : a;
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;
        return new GeoPoint(lat, lng);
    }

    // ------------------------------------------------------------- utilitarios

    private DataLoadResult vacio(List<String> errores, List<String> advertencias) {
        return new DataLoadResult(List.of(), List.of(), List.of(),
                List.copyOf(errores), List.copyOf(advertencias), false, "N/A");
    }

    private JsonNode primeraLista(JsonNode nodo, String[] claves) {
        for (String k : claves) {
            if (nodo.has(k) && nodo.get(k).isArray()) return nodo.get(k);
        }
        return null;
    }

    private JsonNode primero(JsonNode nodo, String[] claves) {
        for (String k : claves) {
            if (nodo.has(k)) return nodo.get(k);
        }
        return null;
    }

    private String texto(JsonNode nodo, String... claves) {
        for (String k : claves) {
            JsonNode v = nodo.path(k);
            if (v.isTextual() && !v.asText().isBlank()) return v.asText();
        }
        return null;
    }
}
