package com.ms4m.fleetsim.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadatos de la documentación interactiva (springdoc) en /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("MS4M · Simulador de Flota — API")
                .version("1.0.0")
                .description("Simulación de camiones mineros sobre una red vial. "
                        + "Endpoints de red, simulación en vivo (SSE) y reporte de velocidades. "
                        + "El contrato completo, decisiones y supuestos están en el README del repositorio."));
    }
}
