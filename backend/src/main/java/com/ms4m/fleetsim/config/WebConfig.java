package com.ms4m.fleetsim.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS configurable por APP_CORS_ORIGINS (coma-separado; "*" por defecto para la demo). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    public WebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(props.getCorsOrigins().split("\\s*,\\s*"))
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}
