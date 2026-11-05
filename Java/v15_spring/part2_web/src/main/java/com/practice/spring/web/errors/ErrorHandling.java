package com.practice.spring.web.errors;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/*
    =====================================================================
    MANEJO DE ERRORES: RESPUESTAS ÚTILES, UNIFORMES Y SEGURAS
    =====================================================================

    Ruta de spring/: guía 7  (part2_web)
    Anterior:  part2_web/.../validation/Validation
    Siguiente: part2_web/.../clients/HttpClients

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: ErrorHandlingTest, con el servidor
      real y peticiones HTTP de verdad, para ver el cuerpo y el
      Content-Type exactos que recibe un cliente. Desde v15_spring/:
          ./mvnw -pl part2_web test -Dtest=ErrorHandlingTest
    - Respuestas citadas: corridas reales con Spring Boot 4.1.2.

    Lo que ya se vio: los errores que Spring responde solo (rest/
    RestControllers, CAPA 6), los dos problemas abiertos de validation/
    Validation (el 400 sin detalles y el 500 de los servicios), las
    excepciones del negocio traducidas en cada puerta (v11_patterns/
    part4_modern/ServiceLayer, CAPA 7), y las buenas prácticas de
    excepciones (v3_exceptions/).

    Importancia: MUY ALTA. Un cliente (otra app, otro equipo) vive de tus
    respuestas de error; y un mensaje interno filtrado es una falla de
    seguridad.

    Índice:
        CAPA 1)  El problema: errores inconsistentes y sin información
        CAPA 2)  ProblemDetail: el formato estándar (RFC 9457)
        CAPA 3)  @RestControllerAdvice: un solo lugar para traducir errores
        CAPA 4)  Las excepciones del negocio no saben de HTTP
        CAPA 5)  Errores de validación con la lista de campos
        CAPA 6)  El 500: genérico para el cliente, completo en el log
        CAPA 7)  Quién gana cuando hay varios manejadores
        CAPA 8)  Errores que no pasan por los controllers
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class ErrorHandling {

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: ERRORES INCONSISTENTES Y SIN INFORMACIÓN
        =================================================================

        Sin hacer nada, una API Spring responde errores así (medido en las
        guías anteriores):
        - un 400 de validación: {"status":400,"error":"Bad Request",...},
          sin decir QUÉ campo está mal;
        - una validación en un servicio: 500, como si el servidor hubiera
          fallado;
        - "producto no existe": lo que cada programador decida (un 404
          vacío, un 200 con {"error":"..."}, un 500...).

        El cliente (la app móvil, el frontend, otro servicio) tiene que
        adivinar el formato de cada error. Lo que se quiere:
        1) un FORMATO ÚNICO para todos los errores;
        2) el CÓDIGO HTTP correcto (4xx culpa del cliente, 5xx del
           servidor: rest/RestControllers, CAPA 4);
        3) información ÚTIL para corregir (qué campo, qué regla);
        4) NADA interno: ni stack traces, ni mensajes de excepciones, ni
           nombres de tablas.
    */

    /*
        =================================================================
        CAPA 2) PROBLEMDETAIL: EL FORMATO ESTÁNDAR (RFC 9457)
        =================================================================

        En vez de inventar un formato, existe un estándar de internet para
        errores de APIs HTTP: "Problem Details for HTTP APIs", RFC 9457
        (antes RFC 7807). Spring lo implementa con la clase ProblemDetail:

            HTTP/1.1 409 Conflict
            Content-Type: application/problem+json

            {
              "type": "https://api.tienda.example/problems/out-of-stock",
              "title": "Sin existencias",
              "status": 409,
              "detail": "Solo hay 2 piezas de CAF-001",
              "instance": "/api/orders",
              "available": 2                       <- campo propio
            }

            type      un URI que IDENTIFICA el tipo de error (estable, para
                      que el cliente decida qué hacer; puede apuntar a una
                      página con la explicación). "about:blank" si no hay
                      uno específico (y entonces no se escribe)
            title     resumen corto del tipo de error
            status    el código HTTP, repetido en el cuerpo
            detail    la explicación de ESTE caso
            instance  qué petición lo produjo
            + campos propios (extensiones): "errors", "available", "errorId"

        Con spring.mvc.problemdetails.enabled=true, Spring responde SUS
        errores con este formato. Medido:

            404  {"detail":"No static resource nada.","instance":"/nada",
                  "status":404,"title":"Not Found"}
            405  {"detail":"Method 'DELETE' is not supported.",...}
            400  {"detail":"Invalid request content.",...}  <- validación:
                                                       SIN la lista de campos
            500  {"timestamp":...,"status":500,"error":"Internal Server
                  Error",...}                         <- tu excepción: formato
                                                       viejo

        Ayuda, pero no basta: la validación sigue sin detalles, y las
        excepciones de TU código no pasan por ahí. Para eso, la CAPA 3.
    */

    @SpringBootApplication
    public static class ErrorsApplication {
    }

    // ---- El negocio: excepciones con significado, SIN nada de HTTP (CAPA 4) ----

    public static class ProductNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ProductNotFoundException(String sku) {
            super("No existe el producto " + sku);
        }
    }

    public static class OutOfStockException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String sku;
        private final int available;

        public OutOfStockException(String sku, int available) {
            super("Solo hay " + available + " piezas de " + sku);
            this.sku = sku;
            this.available = available;
        }

        public String sku() {
            return sku;
        }

        public int available() {
            return available;
        }
    }

    @Service
    @Validated
    public static class InventoryService {
        private final Map<String, Integer> stock = Map.of("CAF-001", 2, "TAZ-002", 10);

        public int availableOf(String sku) {
            Integer units = stock.get(sku);
            if (units == null) {
                throw new ProductNotFoundException(sku);
            }
            return units;
        }

        public String reserve(@NotBlank String sku, @Min(1) int quantity) {   // valida el proxy (Validation, CAPA 8)
            int available = availableOf(sku);
            if (quantity > available) {
                throw new OutOfStockException(sku, available);
            }
            return "reservadas " + quantity + " de " + sku;
        }
    }

    public record OrderRequest(@NotBlank String sku, @Positive int quantity) {
    }

    @RestController
    @RequestMapping("/api")
    public static class OrderController {
        private final InventoryService inventory;

        public OrderController(InventoryService inventory) {
            this.inventory = inventory;
        }

        @PostMapping("/orders")
        public Map<String, String> order(@Valid @RequestBody OrderRequest request) {
            return Map.of("result", inventory.reserve(request.sku(), request.quantity()));
        }

        @GetMapping("/products/{sku}/stock")
        public Map<String, Integer> stock(@PathVariable String sku) {
            return Map.of("available", inventory.availableOf(sku));
        }

        @PostMapping("/reserve")
        public String reserve(@RequestParam String sku, @RequestParam int quantity) {
            return inventory.reserve(sku, quantity);        // sin validar aquí: valida el servicio
        }

        @GetMapping("/bug")
        public String bug() {
            throw new IllegalStateException("conexión a db-prod-07 rechazada para el usuario app_rw");   // interno
        }
    }

    /*
        =================================================================
        CAPA 3) @RESTCONTROLLERADVICE: UN SOLO LUGAR PARA TRADUCIR ERRORES
        =================================================================

        Un @RestControllerAdvice es un bean que aplica a TODOS los
        controllers. Sus métodos @ExceptionHandler(TipoDeExcepción.class)
        se llaman cuando un controller (o algo que llamó) lanza esa
        excepción, y lo que devuelven es la respuesta. Así ningún
        controller tiene try/catch para traducir errores: lo hace este
        lugar, igual para todos (v11_patterns/part4_modern/ServiceLayer,
        CAPA 7: "cada puerta traduce los errores a SU idioma").

        Heredar de ResponseEntityExceptionHandler (de Spring MVC) hace que
        este mismo advice responda con ProblemDetail los errores PROPIOS de
        Spring (404, 405, 415, JSON roto...), y permite personalizar
        cualquiera sobrescribiendo su método (CAPA 5). Con eso ya no hace
        falta spring.mvc.problemdetails.enabled.
    */
    @RestControllerAdvice
    public static class ApiExceptionHandler extends ResponseEntityExceptionHandler {
        private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
        private static final String PROBLEMS = "https://api.tienda.example/problems/";

        /*
            =============================================================
            CAPA 4) LAS EXCEPCIONES DEL NEGOCIO NO SABEN DE HTTP
            =============================================================

            ProductNotFoundException y OutOfStockException son del NEGOCIO:
            no tienen códigos HTTP ni anotaciones web. El advice decide que
            "no existe" es 404 y "sin existencias" es 409. Así el mismo
            servicio se puede usar desde un consumidor de mensajes o una
            tarea programada, que traducen esas excepciones a SU forma
            (v14_architecture/part2_boundaries/Hexagonal, CAPA 6).

            (Existe un atajo: @ResponseStatus(HttpStatus.NOT_FOUND) en la
            clase de la excepción. Funciona, pero pone HTTP en el negocio.
            Úsalo solo para excepciones que de verdad son de la capa web.)

            Y los campos propios del error (available) van como extensiones
            del ProblemDetail: el cliente puede ofrecer "¿quieres las 2 que
            hay?" sin leer el texto.
        */
        @ExceptionHandler(ProductNotFoundException.class)
        public ProblemDetail notFound(ProductNotFoundException error) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
            problem.setType(URI.create(PROBLEMS + "product-not-found"));
            problem.setTitle("Producto no encontrado");
            return problem;
        }

        @ExceptionHandler(OutOfStockException.class)
        public ProblemDetail outOfStock(OutOfStockException error) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
            problem.setType(URI.create(PROBLEMS + "out-of-stock"));
            problem.setTitle("Sin existencias");
            problem.setProperty("sku", error.sku());        // extensiones: datos útiles para el cliente
            problem.setProperty("available", error.available());
            return problem;
        }

        /*
            =============================================================
            CAPA 5) ERRORES DE VALIDACIÓN CON LA LISTA DE CAMPOS
            =============================================================

            Los dos casos abiertos de validation/Validation:

            1) @Valid @RequestBody que falla: MethodArgumentNotValidException.
               Se sobrescribe el método de ResponseEntityExceptionHandler que
               la maneja, y se agrega la lista "errors": por cada campo, su
               RUTA, el CÓDIGO de la regla (el nombre de la restricción:
               "NotBlank", "Positive"; estable, no cambia con el idioma) y el
               MENSAJE (en el idioma de la petición; Validation, CAPA 7).

            2) La validación de un SERVICIO: ConstraintViolationException.
               Spring no la conoce como error del cliente (por eso daba 500).
               Un @ExceptionHandler la convierte en 400, con el mismo formato.

            Mismo "type" para los dos: para el cliente es el mismo tipo de
            problema ("datos inválidos"), venga de donde venga.
        */
        @Override
        protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
                HttpHeaders headers, HttpStatusCode status, WebRequest request) {
            List<FieldProblem> errors = error.getBindingResult().getFieldErrors().stream()
                    .map(field -> new FieldProblem(field.getField(), field.getCode(), field.getDefaultMessage()))
                    .sorted(Comparator.comparing(FieldProblem::field).thenComparing(FieldProblem::code))
                    .toList();
            return ResponseEntity.badRequest().body(invalidData(errors));
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ProblemDetail invalidParameters(ConstraintViolationException error) {
            List<FieldProblem> errors = error.getConstraintViolations().stream()
                    .map(violation -> new FieldProblem(
                            violation.getPropertyPath().toString(),
                            violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                            violation.getMessage()))
                    .sorted(Comparator.comparing(FieldProblem::field))
                    .toList();
            return invalidData(errors);
        }

        private static ProblemDetail invalidData(List<FieldProblem> errors) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    errors.size() + " dato(s) inválido(s)");
            problem.setType(URI.create(PROBLEMS + "invalid-data"));
            problem.setTitle("Datos inválidos");
            problem.setProperty("errors", errors);
            return problem;
        }

        /*
            =============================================================
            CAPA 6) EL 500: GENÉRICO PARA EL CLIENTE, COMPLETO EN EL LOG
            =============================================================

            Lo que no se esperaba (un error de programación, la base de
            datos caída) termina aquí, en un manejador de Exception, el
            último recurso:

            - al CLIENTE: un 500 GENÉRICO. Nunca el mensaje de la excepción:
              puede contener nombres de servidores, usuarios, tablas o datos
              de otros clientes (el ejemplo de /api/bug tiene un servidor y un
              usuario de base de datos). Es una de las filtraciones más
              comunes, y por eso la regla de tu organización de no compartir
              información interna aplica también a las respuestas de una API;
            - al LOG: TODO, con el stack trace, para quien lo investigue;
            - y un ERROR ID que va en los dos: el cliente lo reporta ("me
              salió el error 3f2a..."), y en el log se busca ese id. Conecta
              la queja con el stack trace sin exponer nada.

            Medido: con este manejador, /api/bug responde 500 con
            {"title":"Error interno","detail":"Ocurrió un error inesperado.
            Repórtalo con el errorId.","errorId":"..."} y el texto de la
            excepción solo aparece en el log.
        */
        @ExceptionHandler(Exception.class)
        public ProblemDetail unexpected(Exception error) {
            String errorId = UUID.randomUUID().toString();
            LOG.error("Error inesperado [errorId={}]", errorId, error);   // completo, solo en el log
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Ocurrió un error inesperado. Repórtalo con el errorId.");
            problem.setTitle("Error interno");
            problem.setProperty("errorId", errorId);
            return problem;
        }
    }

    public record FieldProblem(String field, String code, String message) {
    }

    /*
        =================================================================
        CAPA 7) QUIÉN GANA CUANDO HAY VARIOS MANEJADORES
        =================================================================

        Para una excepción, Spring busca en este orden:
        1) un @ExceptionHandler DENTRO del mismo controller (solo aplica a
           ese controller);
        2) los @RestControllerAdvice (si hay varios, el orden lo da @Order;
           y se pueden limitar a ciertos paquetes o controllers:
           @RestControllerAdvice(basePackages = "...")).
        Y dentro de cada uno, gana el manejador del tipo MÁS ESPECÍFICO:
        ProductNotFoundException antes que RuntimeException, antes que
        Exception. Por eso el manejador de Exception (CAPA 6) no "se come" a
        los demás.

        LegacyController tiene su propio manejador de
        IllegalArgumentException (responde 422): gana sobre el advice para
        SUS métodos, y para los demás controllers no existe. Útil para un
        caso especial; si se repite, va al advice.
    */
    @RestController
    @RequestMapping("/api/legacy")
    public static class LegacyController {
        @GetMapping("/discount")
        public String discount(@RequestParam String code) {
            throw new IllegalArgumentException("Cupón inválido: " + code);
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ProblemDetail invalidCoupon(IllegalArgumentException error) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, error.getMessage());
        }
    }

    /*
        =================================================================
        CAPA 8) ERRORES QUE NO PASAN POR LOS CONTROLLERS
        =================================================================

        El advice solo ve las excepciones que pasan por Spring MVC (los
        controllers y lo que ellos llaman). Hay errores que ocurren ANTES:
        en los filtros de la petición (por ejemplo, Spring Security
        rechazando a alguien sin sesión: part5_security), o en el propio
        servidor.

        Esos terminan en la ruta /error de Spring Boot (BasicErrorController),
        que arma la respuesta con el formato de rest/RestControllers, CAPA 6.
        Se ajusta con propiedades:

            server.error.include-message=never        (por defecto: never)
            server.error.include-stacktrace=never     (por defecto: never)
            server.error.include-binding-errors=never

        NUNCA las pongas en "always" en producción: es exactamente la
        filtración de la CAPA 6. En desarrollo, a veces se usan para ver el
        mensaje sin abrir el log.

        La seguridad tiene sus propios manejadores para 401 y 403
        (part5_security).
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Cada controller con su try/catch y su propio formato de error
           (CAPAS 1 y 3).

        2) 200 con {"error": "..."} adentro; 500 para errores del cliente
           (CAPA 1).

        3) Inventar un formato cuando existe ProblemDetail (CAPA 2).

        4) Códigos HTTP dentro de las excepciones del negocio (CAPA 4).

        5) Errores de validación sin la lista de campos, o con solo el
           mensaje traducido y sin un código estable (CAPA 5).

        6) Devolver e.getMessage() o el stack trace en un 500; no registrar
           el error en el log; no tener un id para encontrarlo (CAPA 6).

        7) Un manejador de Exception que también atrapa lo que debía tener su
           propio código (por no tener los específicos) (CAPA 7).

        8) server.error.include-stacktrace=always en producción (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Un formato único, el código correcto, información útil y nada
          interno.
        - ProblemDetail (RFC 9457): type, title, status, detail, instance y
          extensiones; application/problem+json.
        - @RestControllerAdvice + @ExceptionHandler, heredando de
          ResponseEntityExceptionHandler para cubrir también los errores de
          Spring.
        - Excepciones del negocio sin HTTP; el advice las traduce (404, 409).
        - Validación: 400 con "errors" (campo, código, mensaje), del cuerpo
          y de los servicios.
        - 500 genérico + errorId; todo el detalle, solo en el log.
        - El manejador del controller gana al advice; el tipo más específico
          gana al general.
        - Lo que falla antes de los controllers va a /error; nunca exponer
          mensajes ni stack traces.
    */
}
