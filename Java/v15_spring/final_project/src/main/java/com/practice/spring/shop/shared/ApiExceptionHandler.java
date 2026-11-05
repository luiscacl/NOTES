package com.practice.spring.shop.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todos los errores de la API como ProblemDetail (part2_web/.../errors/ErrorHandling).
 * Hereda de ResponseEntityExceptionHandler: los errores propios de Spring MVC (JSON mal formado,
 * método no permitido...) ya salen como ProblemDetail. Los de seguridad (401, 403) NO pasan por
 * aquí: ocurren en los filtros, antes de llegar a un controller (part5_security/.../SecurityBasics).
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler
    ProblemDetail notFound(NotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("No encontrado");
        return problem;
    }

    @ExceptionHandler
    ProblemDetail businessRule(BusinessRuleException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Regla del negocio");
        problem.setProperty("code", e.code());
        return problem;
    }

    /**
     * ?sort=password: sin esto era un 500 (medido), un error del CLIENTE contado como falla nuestra.
     * Spring Data 4 la movió a org.springframework.data.core (en Boot 3: ...data.mapping).
     */
    @ExceptionHandler
    ProblemDetail unknownSortProperty(PropertyReferenceException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "No se puede ordenar por '" + e.getPropertyName() + "'");
        problem.setTitle("Datos inválidos");
        return problem;
    }

    /** Lo inesperado: 500 sin detalles internos, y un id para encontrarlo en el log. */
    @ExceptionHandler
    ProblemDetail unexpected(Exception e) {
        String errorId = UUID.randomUUID().toString();
        LOG.error("Error inesperado {}", errorId, e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Reporta este id: " + errorId);
        problem.setTitle("Error interno");
        problem.setProperty("errorId", errorId);
        return problem;
    }

    /** 400 con QUÉ campo falló y por qué, no solo "Invalid request content". */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "La petición tiene datos inválidos");
        problem.setTitle("Datos inválidos");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status).body(problem);
    }
}
