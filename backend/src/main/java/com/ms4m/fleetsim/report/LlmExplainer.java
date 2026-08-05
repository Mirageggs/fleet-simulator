package com.ms4m.fleetsim.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Explicación opcional con un LLM (API de Anthropic). Deshabilitado por
 * defecto; se activa con APP_LLM_ENABLED=true y ANTHROPIC_API_KEY.
 *
 * Diseño defensivo exigido por el enunciado: ante CUALQUIER problema (sin red,
 * clave inválida, timeout, respuesta inesperada) devuelve Optional.empty() y
 * el ReportService usa la explicación heurística como respaldo.
 */
public class LlmExplainer {

    private static final String INSTRUCCION = """
            Eres analista de despacho en una operación minera. A partir del siguiente JSON \
            con estadísticas de velocidad de camiones (km/h), redacta en español un análisis \
            breve (máximo 120 palabras) para un supervisor: promedio de flota, camión más \
            rápido y más lento, y cualquier dato atípico. No inventes cifras que no estén \
            en el JSON y no uses formato Markdown.

            """;

    private final String modelo;
    private final String apiKey;
    private final ObjectMapper json;

    public LlmExplainer(String modelo, String apiKey, ObjectMapper json) {
        this.modelo = modelo;
        this.apiKey = apiKey;
        this.json = json;
    }

    public Optional<String> explicar(String estadisticasJson) {
        try {
            String cuerpo = json.writeValueAsString(Map.of(
                    "model", modelo,
                    "max_tokens", 400,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", INSTRUCCION + estadisticasJson))));

            HttpRequest peticion = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                    .timeout(Duration.ofSeconds(12))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();

            HttpClient cliente = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) return Optional.empty();

            JsonNode nodo = json.readTree(respuesta.body());
            String texto = nodo.path("content").path(0).path("text").asText("");
            return texto.isBlank() ? Optional.empty() : Optional.of(texto.trim());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
