package com.ms4m.fleetsim.web;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.NoSuchElementException;

/**
 * Respuestas de error controladas y consistentes: siempre {"mensaje": "..."}.
 * 409 para estados inválidos (p. ej. datos no operativos), 404 para recursos
 * inexistentes (p. ej. reporte sin simulación previa) y 500 para lo demás.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorDto(String mensaje) { }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorDto> conflicto(IllegalStateException e) {
        return ResponseEntity.status(409).body(new ErrorDto(e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> cuerpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.status(400)
                .body(new ErrorDto("Cuerpo de la petición inválido: se esperaba JSON como {\"semilla\": 42}."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorDto> rutaInexistente(NoResourceFoundException e) {
        return ResponseEntity.status(404)
                .body(new ErrorDto("Ruta no encontrada. Consulta /swagger-ui.html para ver los endpoints."));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorDto> noEncontrado(NoSuchElementException e) {
        return ResponseEntity.status(404).body(new ErrorDto(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> interno(Exception e) {
        return ResponseEntity.status(500)
                .body(new ErrorDto("Error interno del servidor: " + e.getMessage()));
    }
}
