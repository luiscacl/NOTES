package com.practice.spring.web.rest;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/*
    =====================================================================
    CONTROLLERS REST: DE LA PETICIÓN HTTP A TU MÉTODO, Y DE VUELTA
    =====================================================================

    Ruta de spring/: guía 5  (part2_web)
    Anterior:  part1_core/.../config/ExternalConfiguration
    Siguiente: part2_web/.../validation/Validation

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: RestControllersTest (con un servidor
      Tomcat REAL en un puerto libre y peticiones HTTP de verdad) y
      RestControllersSliceTest (sin servidor, con MockMvcTester). Desde
      v15_spring/:
          ./mvnw -pl part2_web test -Dtest='RestControllers*'
    - Para correr la API y probarla a mano: ejecuta el main de
      CatalogApplication desde el editor, y en otra terminal:
          curl http://localhost:8080/api/products
    - Todo lo citado (JSON, códigos, nombres de hilos) salió de corridas
      reales con Spring Boot 4.1.2.

    Lo que ya se vio: HTTP desde el cliente (v8_apis/part2_io_and_network/
    HttpClientUsage), DTOs (v11_patterns/part4_modern/DTO), capas y
    controllers (v14_architecture/part1_layers/LayeredArchitecture), hilos
    virtuales (v13_concurrency/part3_tasks/VirtualThreads).

    Importancia: MUY ALTA. Es lo que más vas a escribir en Spring Boot.

    Índice:
        CAPA 1)  El recorrido de una petición
        CAPA 2)  @RestController y los mapeos
        CAPA 3)  Leer lo que llega: ruta, parámetros, cuerpo, encabezados
        CAPA 4)  Responder: ResponseEntity y los códigos de estado
        CAPA 5)  JSON con Jackson
        CAPA 6)  Los errores que Spring responde solo
        CAPA 7)  Diseñar una API REST
        CAPA 8)  Probar controllers: con servidor y sin servidor
        CAPA 9)  Un hilo por petición, y los hilos virtuales
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class RestControllers {

    /*
        =================================================================
        CAPA 1) EL RECORRIDO DE UNA PETICIÓN
        =================================================================

        curl -X POST http://localhost:8080/api/products -d '{...}'

            1) TOMCAT (el servidor embebido, que trae el starter webmvc)
               escucha en un socket TCP del puerto 8080. Un hilo acepta la
               conexión, otro detecta cuándo llegaron datos (con el
               mecanismo de E/S sin bloqueo del SO: epoll en Linux), y
               entrega la petición a un HILO TRABAJADOR de su pool (CAPA 9).
            2) El DISPATCHERSERVLET, la única puerta de entrada de Spring MVC
               (el patrón "front controller"), busca qué método atiende
               "POST /api/products" (HandlerMapping).
            3) Los "argument resolvers" arman los parámetros del método:
               sacan {id} de la ruta, ?category= de la URL, y convierten el
               cuerpo JSON en tu record con Jackson (CAPA 5).
            4) Se llama TU método.
            5) Lo que devuelve se convierte en la respuesta: un
               HttpMessageConverter lo pasa a JSON, y se ponen el código de
               estado y los encabezados (CAPA 4).
            6) Si algo lanzó una excepción, se traduce a una respuesta de
               error (CAPA 6; part2_web/.../errors/ErrorHandling).

        Tu controller solo hace el paso 4. Todo lo demás lo hace Spring, y
        por eso un controller debe ser DELGADO: traducir HTTP a una llamada
        al servicio, y la respuesta del servicio a HTTP
        (v14_architecture/part1_layers/LayeredArchitecture, CAPA 2).
    */
    @SpringBootApplication
    public static class CatalogApplication {
        public static void main(String[] args) {
            org.springframework.boot.SpringApplication.run(CatalogApplication.class, args);
        }
    }

    // ---- El "servicio" que usan los controllers (en memoria; part3_data lo hará con base de datos) ----

    public record Product(long id, String name, String category, long priceCents, Instant createdAt) {
    }

    @Service
    public static class ProductCatalog {
        private final Map<Long, Product> products = new ConcurrentHashMap<>();   // varios hilos a la vez (CAPA 9)
        private final AtomicLong nextId = new AtomicLong(1);

        public ProductCatalog() {
            add("Café de Chiapas", "café", 18_000);          // privado: el constructor no llama a métodos
            add("Taza de barro", "cocina", 12_000);           // que una subclase podría cambiar
            add("Café de Veracruz", "café", 16_500);
        }

        public Product create(String name, String category, long priceCents) {
            return add(name, category, priceCents);
        }

        private Product add(String name, String category, long priceCents) {
            long id = nextId.getAndIncrement();
            Product product = new Product(id, name, category, priceCents, Instant.parse("2026-11-04T10:00:00Z"));
            products.put(id, product);
            return product;
        }

        public Optional<Product> find(long id) {
            return Optional.ofNullable(products.get(id));
        }

        public List<Product> search(String category, Long maxPriceCents) {
            return products.values().stream()
                    .filter(p -> category == null || p.category().equals(category))
                    .filter(p -> maxPriceCents == null || p.priceCents() <= maxPriceCents)
                    .sorted(Comparator.comparingLong(Product::id))
                    .toList();
        }

        public Optional<Product> replace(long id, String name, String category, long priceCents) {
            return find(id).map(old -> {
                Product updated = new Product(id, name, category, priceCents, old.createdAt());
                products.put(id, updated);
                return updated;
            });
        }

        public Optional<Product> changePrice(long id, long priceCents) {
            return find(id).map(old -> replace(id, old.name(), old.category(), priceCents).orElseThrow());
        }

        public boolean delete(long id) {
            return products.remove(id) != null;
        }
    }

    /*
        =================================================================
        CAPA 2) @RESTCONTROLLER Y LOS MAPEOS
        =================================================================

        @RestController = @Controller + @ResponseBody: es un bean (container/
        IoCContainer) cuyos métodos atienden peticiones y cuyo VALOR DE
        RETORNO se escribe en el cuerpo de la respuesta (como JSON).

        @RequestMapping("/api/products") en la clase pone el prefijo común;
        en cada método, el verbo HTTP y el resto de la ruta:

            @GetMapping              GET    /api/products
            @GetMapping("/{id}")     GET    /api/products/7
            @PostMapping             POST   /api/products
            @PutMapping("/{id}")     PUT    /api/products/7
            @PatchMapping("/{id}/price")  PATCH /api/products/7/price
            @DeleteMapping("/{id}")  DELETE /api/products/7

        Si llega un verbo que la ruta no tiene (DELETE donde solo hay GET),
        Spring responde 405 Method Not Allowed solo (CAPA 6).
    */
    @RestController
    @RequestMapping("/api/products")
    public static class ProductController {
        private final ProductCatalog catalog;               // inyectado: el controller solo traduce

        public ProductController(ProductCatalog catalog) {
            this.catalog = catalog;
        }

        /*
            =============================================================
            CAPA 3) LEER LO QUE LLEGA: RUTA, PARÁMETROS, CUERPO,
                    ENCABEZADOS
            =============================================================

                @PathVariable long id          /api/products/{id}: parte de
                                               la RUTA (identifica el
                                               recurso)
                @RequestParam String category  ?category=café: filtros y
                                               opciones. required = false o
                                               defaultValue para opcionales
                @RequestBody CreateProductRequest   el cuerpo JSON, convertido
                                               al record (CAPA 5)
                @RequestHeader("X-Request-Id") un encabezado

            Spring CONVIERTE los textos al tipo del parámetro: "7" -> long.
            Si no puede ("abc" donde va un long), responde 400 sin llegar a
            tu método.

            El cuerpo se recibe en un DTO de ENTRADA (un record con solo lo
            que el cliente puede mandar), nunca en la entidad
            (v11_patterns/part4_modern/DTO, CAPA 4). Validar esos datos es
            la siguiente guía (part2_web/.../validation/Validation).
        */
        @GetMapping
        public List<ProductResponse> search(@RequestParam(required = false) String category,
                                            @RequestParam(required = false) Long maxPriceCents) {
            return catalog.search(category, maxPriceCents).stream().map(ProductResponse::from).toList();
        }

        /*
            =============================================================
            CAPA 4) RESPONDER: RESPONSEENTITY Y LOS CÓDIGOS DE ESTADO
            =============================================================

            Si el método devuelve un objeto, la respuesta es 200 OK con ese
            objeto en JSON. Para controlar el CÓDIGO y los ENCABEZADOS, se
            devuelve un ResponseEntity<T>:

                ResponseEntity.ok(body)                       200
                ResponseEntity.created(uri).body(body)        201 + Location
                ResponseEntity.noContent().build()            204, sin cuerpo
                ResponseEntity.notFound().build()             404
                ResponseEntity.of(optional)                   200 o 404, según
                                                              el Optional

            O, para un código fijo, @ResponseStatus(HttpStatus.NO_CONTENT)
            en el método.

            Los códigos que importan en una API:

                200 OK              lectura o cambio exitoso, con cuerpo
                201 Created         se creó; Location dice dónde está
                204 No Content      éxito sin nada que devolver (DELETE)
                400 Bad Request     lo que mandó el cliente está mal
                401 Unauthorized    no se identificó (part5_security)
                403 Forbidden       se identificó, pero no tiene permiso
                404 Not Found       no existe
                409 Conflict        choca con el estado actual (duplicado,
                                    versión vieja)
                422 Unprocessable   bien formado, pero viola una regla de
                                    negocio
                500 Internal Error  falló el servidor (un error nuestro)
                503 Unavailable     temporalmente no disponible

            La regla: 4xx = el cliente debe cambiar algo; 5xx = el problema
            es del servidor, reintentar puede funcionar.
        */
        @GetMapping("/{id}")
        public ResponseEntity<ProductResponse> find(@PathVariable long id) {
            return ResponseEntity.of(catalog.find(id).map(ProductResponse::from));   // 200 o 404
        }

        @PostMapping
        public ResponseEntity<ProductResponse> create(@RequestBody CreateProductRequest request,
                                                      @RequestHeader(name = "X-Request-Id", required = false) String requestId) {
            Product created = catalog.create(request.name(), request.category(), request.priceCents());
            return ResponseEntity.created(URI.create("/api/products/" + created.id()))   // 201 + Location
                    .header("X-Request-Id", requestId == null ? "none" : requestId)   // devolverlo ayuda a rastrear
                    .body(ProductResponse.from(created));
        }

        @PutMapping("/{id}")
        public ResponseEntity<ProductResponse> replace(@PathVariable long id, @RequestBody CreateProductRequest request) {
            return ResponseEntity.of(catalog.replace(id, request.name(), request.category(), request.priceCents())
                    .map(ProductResponse::from));
        }

        @PatchMapping("/{id}/price")
        public ResponseEntity<ProductResponse> changePrice(@PathVariable long id, @RequestBody PriceChange change) {
            return ResponseEntity.of(catalog.changePrice(id, change.priceCents()).map(ProductResponse::from));
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)              // 204 si se borró
        public void delete(@PathVariable long id) {
            if (!catalog.delete(id)) {
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
    }

    /*
        =================================================================
        CAPA 5) JSON CON JACKSON
        =================================================================

        Jackson es la librería que convierte objetos a JSON y de vuelta (la
        mencionada en v8_apis/part2_io_and_network/Serialization). Con
        records no hace falta nada: usa los nombres de los componentes.

        Medido con Spring Boot 4.1.2:
        - un record con Instant y LocalDate sale así:
              {"id":1,"name":"Café","createdAt":"2026-11-04T10:00:00Z",
               "available":"2026-12-01","note":null}
          Fechas en texto ISO 8601 (lo correcto para APIs), y los null SÍ
          aparecen ("note":null);
        - un JSON con un campo que el record no tiene ("hacker": true) se
          acepta y el campo se IGNORA (por eso la asignación masiva de
          v11_patterns/part4_modern/DTO, CAPA 4, no funciona contra un DTO);
        - JSON roto o un texto donde va un número: 400 Bad Request, sin
          llegar al controller.

        Lo que se ajusta:
        - por propiedad: spring.jackson.* (por ejemplo, no escribir los null:
          spring.jackson.default-property-inclusion=non_null);
        - por campo: @JsonProperty("product_id") para otro nombre en el JSON,
          @JsonInclude(NON_NULL), @JsonIgnore.

        DIFERENCIA IMPORTANTE ENTRE VERSIONES: Spring Boot 4 usa JACKSON 3,
        cuyos paquetes son tools.jackson.* (tools.jackson.databind.json.
        JsonMapper). Spring Boot 3 usa Jackson 2: com.fasterxml.jackson.*
        (com.fasterxml.jackson.databind.ObjectMapper). Las ANOTACIONES
        (@JsonProperty, @JsonIgnore...) siguen en com.fasterxml.jackson.
        annotation en los dos (verificado con dependency:tree). Si copias
        código de un proyecto Boot 3, revisa los imports de Jackson.
    */
    public record CreateProductRequest(String name, String category, long priceCents) {
    }

    public record PriceChange(long priceCents) {
    }

    public record ProductResponse(long id, String name, String category, long priceCents, Instant createdAt) {
        static ProductResponse from(Product product) {      // el mapeo vive en el DTO (DTO, CAPA 3)
            return new ProductResponse(product.id(), product.name(), product.category(),
                    product.priceCents(), product.createdAt());
        }
    }

    /*
        =================================================================
        CAPA 6) LOS ERRORES QUE SPRING RESPONDE SOLO
        =================================================================

        Antes de llegar a tu código, Spring ya responde estos casos. Medido,
        con el cuerpo que manda por defecto:

            ruta que no existe          404 {"timestamp":"...","status":404,
                                             "error":"Not Found","path":"/nada"}
            verbo que la ruta no tiene  405 Method Not Allowed
            JSON roto o de otro tipo    400 Bad Request
            Content-Type text/plain     415 Unsupported Media Type
            Accept: application/xml     406 Not Acceptable (sin cuerpo)
            tu código lanza una         500 {"...","status":500,"error":
            excepción no manejada            "Internal Server Error","path":...}

        Fíjate en el 500: el mensaje de la excepción ("secreto interno" en
        la prueba) NO aparece en la respuesta. Bien: los detalles internos
        no deben llegarle al cliente. Pero el cliente tampoco sabe QUÉ hizo
        mal en un 400. Darle respuestas de error útiles y uniformes
        (ProblemDetail) es part2_web/.../errors/ErrorHandling.

        En DELETE de un id que no existe, el controller lanza
        ResponseStatusException(NOT_FOUND): la forma corta de "responde con
        este código".
    */

    /*
        =================================================================
        CAPA 7) DISEÑAR UNA API REST
        =================================================================

        REST (Roy Fielding, 2000) es un estilo, no un estándar. Lo que se
        considera buena práctica:

        - Las URLs nombran RECURSOS (sustantivos, en plural); el VERBO HTTP
          dice qué se hace:
              bien:  POST /api/products        mal: POST /api/createProduct
                     DELETE /api/products/7         GET /api/deleteProduct?id=7
        - Cada verbo tiene un significado (HTTP, RFC 9110):

              verbo    para qué                    seguro*  idempotente**
              GET      leer                        sí       sí
              POST     crear / acción              no       no
              PUT      REEMPLAZAR completo         no       sí
              PATCH    cambiar una parte           no       depende
              DELETE   borrar                      no       sí
              * no cambia nada   ** repetirlo da lo mismo que hacerlo una vez

          Que GET sea seguro importa: navegadores, cachés y buscadores
          repiten los GET libremente. Que PUT y DELETE sean idempotentes
          permite reintentarlos (v14_architecture/part3_systems/
          CommandsQueriesAndConsistency, CAPA 7). POST no: por eso un
          reintento de POST puede crear dos pedidos.
        - Filtros, orden y paginación como parámetros: GET
          /api/products?category=café&maxPriceCents=17000&page=0&size=20.
        - Relaciones como subrecursos: GET /api/orders/7/lines.
        - Versionar cuando hay que romper el contrato: /api/v2/products
          (DTO, CAPA 8).
        - Códigos de estado correctos (CAPA 4), nunca 200 con
          {"error": "..."} adentro.
    */

    /*
        =================================================================
        CAPA 8) PROBAR CONTROLLERS: CON SERVIDOR Y SIN SERVIDOR
        =================================================================

        Dos formas, que se complementan (part4_testing las verá completas):

        1) SIN SERVIDOR, solo la capa web: @WebMvcTest(ProductController.class)
           arranca SOLO lo de Spring MVC y ese controller (no los demás
           beans), y MockMvcTester simula peticiones sin abrir ningún
           puerto. Rápido; para probar mapeos, códigos, JSON y errores.
           (RestControllersSliceTest)

        2) CON EL SERVIDOR REAL: @SpringBootTest(webEnvironment = RANDOM_PORT)
           arranca la aplicación completa con Tomcat en un puerto libre, y
           RestTestClient (nuevo en Spring Framework 7) hace peticiones HTTP
           de verdad. Más lento; prueba todo junto. (RestControllersTest)

        En Spring Boot 4 las anotaciones de prueba cambiaron de paquete: por
        ejemplo, @WebMvcTest ahora está en org.springframework.boot.webmvc.
        test.autoconfigure (en Boot 3: org.springframework.boot.test.
        autoconfigure.web.servlet). Verificado en los jars.
    */

    /*
        =================================================================
        CAPA 9) UN HILO POR PETICIÓN, Y LOS HILOS VIRTUALES
        =================================================================

        Tomcat atiende cada petición en un hilo de su pool, de principio a
        fin: mientras tu método espera a la base de datos, ese hilo espera
        con él. Por defecto el pool tiene hasta 200 hilos
        (server.tomcat.threads.max). Medido: la petición la atendió el hilo
        "http-nio-auto-1-exec-10" (virtual=false).

        Dos consecuencias:
        1) El controller y los servicios son SINGLETONS que atienden a todos
           esos hilos a la vez: nada de estado de una petición en sus campos
           (container/IoCContainer, CAPA 5). Por eso ProductCatalog usa un
           ConcurrentHashMap (v13_concurrency/part2_coordination/
           ConcurrentCollections).
        2) Con 200 hilos y peticiones que esperan mucho, la petición 201
           hace fila con la CPU ociosa (v13_concurrency/part3_tasks/
           VirtualThreads, CAPA 1).

        La solución actual es una línea:
            spring.threads.virtual.enabled=true
        Medido: con ella, la misma petición la atendió "tomcat-handler-9"
        con virtual=true. Cada petición en su propio hilo virtual, sin el
        límite de 200, con el mismo código.
    */
    @RestController
    public static class DiagnosticsController {
        @GetMapping("/api/diagnostics/thread")
        public Map<String, Object> thread() {
            Thread current = Thread.currentThread();
            return Map.of("name", current.getName(), "virtual", current.isVirtual());
        }
    }

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Lógica de negocio en el controller (CAPA 1).

        2) Verbos en las URLs (/createProduct) o GET que cambian cosas
           (CAPA 7).

        3) Recibir o devolver entidades en vez de DTOs (CAPAS 3 y 5).

        4) Siempre 200, con el error escondido en el cuerpo; 500 para
           errores del cliente (CAPA 4).

        5) Copiar código de Jackson 2 (com.fasterxml.jackson.databind) a un
           proyecto Boot 4, o al revés (CAPA 5).

        6) Exponer mensajes de excepciones internas al cliente (CAPA 6).

        7) Solo pruebas con servidor (lentas) o solo sin servidor (no
           prueban la integración) (CAPA 8).

        8) Estado de la petición en campos del controller (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Tomcat recibe, DispatcherServlet enruta, Jackson convierte; tu
          método solo traduce entre HTTP y el servicio.
        - @RestController + @RequestMapping y @Get/Post/Put/Patch/DeleteMapping.
        - @PathVariable (qué recurso), @RequestParam (filtros),
          @RequestBody (un DTO record), @RequestHeader.
        - ResponseEntity para código y encabezados: 201 + Location, 204, 404.
        - JSON: fechas ISO, null incluidos, campos desconocidos ignorados;
          Boot 4 = Jackson 3 (tools.jackson), Boot 3 = Jackson 2.
        - Spring responde solo 400, 404, 405, 406, 415 y 500 (sin filtrar el
          mensaje interno).
        - REST: sustantivos en plural, verbos con significado, idempotencia.
        - Pruebas: @WebMvcTest + MockMvcTester sin servidor; @SpringBootTest
          + RestTestClient con servidor real.
        - Un hilo por petición; spring.threads.virtual.enabled=true.
    */
}
