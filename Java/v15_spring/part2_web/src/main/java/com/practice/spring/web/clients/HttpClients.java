package com.practice.spring.web.clients;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.registry.ImportHttpServices;

/*
    =====================================================================
    CLIENTES HTTP: LLAMAR A OTRAS APIS DESDE SPRING
    =====================================================================

    Ruta de spring/: guía 8  (part2_web)
    Anterior:  part2_web/.../errors/ErrorHandling
    Siguiente: part3_data/...  (la primera de datos: JdbcAndTransactions)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: HttpClientsTest. La prueba levanta un
      "proveedor de pagos" FALSO, pero real: un servidor HTTP del JDK en un
      puerto libre, que responde, falla, tarda y rechaza como uno de
      verdad. La aplicación lo llama por HTTP. Desde v15_spring/:
          ./mvnw -pl part2_web test -Dtest=HttpClientsTest
    - Nombres de clases y propiedades verificados en los jars de Spring
      Framework 7.0.10 y Spring Boot 4.1.2.

    Lo que ya se vio: el cliente HTTP del JDK y los timeouts (v8_apis/
    part2_io_and_network/HttpClientUsage), adaptadores que traducen los
    errores de un proveedor (v14_architecture/part2_boundaries/Hexagonal,
    CAPA 5), fallas parciales y circuit breaker (v14_architecture/
    part3_systems/MonolithVsMicroservices, CAPA 4), idempotencia
    (v14_architecture/part3_systems/CommandsQueriesAndConsistency, CAPA 7),
    ProblemDetail (errors/ErrorHandling).

    Importancia: ALTA. Casi todo servicio llama a otros: pagos, envíos,
    otro equipo de la empresa.

    Índice:
        CAPA 1)  Las opciones en Spring, y cuál usar
        CAPA 2)  RestClient: el builder de Boot
        CAPA 3)  Errores: traducir los del proveedor a los tuyos
        CAPA 4)  Tiempos límite: siempre
        CAPA 5)  Clientes declarativos: una interface y Spring la implementa
        CAPA 6)  Reintentos: solo lo que se puede repetir
        CAPA 7)  Probar un cliente
        CAPA 8)  Errores comunes
        RESUMEN FINAL
*/
public class HttpClients {

    /*
        =================================================================
        CAPA 1) LAS OPCIONES EN SPRING, Y CUÁL USAR
        =================================================================

            RestTemplate   el clásico (2009). Funciona y está en todo código
                           existente, pero ya no recibe novedades. Para leerlo,
                           sí; para código nuevo, no.
            WebClient      del mundo reactivo (Spring WebFlux): no bloquea,
                           devuelve Mono/Flux. Útil solo si toda la aplicación
                           es reactiva.
            RestClient     (Spring 6.1, 2023) el reemplazo de RestTemplate
                           con una API fluida moderna. Bloqueante: con hilos
                           virtuales eso ya no es un problema (rest/
                           RestControllers, CAPA 9). EL QUE SE USA HOY en
                           aplicaciones Spring MVC.
            Interfaces HTTP  (@HttpExchange) declaras una interface con
                           anotaciones y Spring genera la implementación
                           (CAPA 5), sobre RestClient.

        Un detalle de Spring Boot 4: el RestClient configurado por Boot (con
        sus conversores de JSON, tiempos límite y métricas) está en su propio
        starter, spring-boot-starter-restclient. El starter webmvc NO lo trae.
        Verificado quitándolo del pom:

            Parameter 0 of constructor in ...HttpClients$PaymentsGateway
            required a bean of type
            'org.springframework.web.client.RestClient$Builder' that could
            not be found.

        En Boot 3 venía incluido con el starter web.

        Y una trampa que apareció al verificarlo: con SOLO el starter de
        pruebas (spring-boot-starter-restclient-test, scope test), las
        pruebas PASAN, porque ese starter trae el mismo módulo como
        dependencia transitiva. Pero en producción las dependencias de test
        no existen, y la aplicación no arrancaría. Que las pruebas pasen no
        prueba que el pom esté bien: lo que el código principal usa va con
        scope compile (part1_core/.../startup/SpringAndBoot, CAPA 3).
    */
    @SpringBootApplication
    @EnableConfigurationProperties(PaymentsProperties.class)
    @EnableResilientMethods                                 // activa @Retryable (CAPA 6)
    @ImportHttpServices(group = "payments", types = PaymentsApi.class)   // CAPA 5
    public static class ShopApplication {
    }

    @ConfigurationProperties("payments")
    public record PaymentsProperties(String baseUrl) {     // payments.base-url (config/ExternalConfiguration)
    }

    // ---- El contrato del proveedor de pagos (su JSON) ----

    public record ChargeRequest(String customerEmail, long amountCents) {
    }

    public record Charge(String id, String status, long amountCents) {
    }

    // ---- Las excepciones de NUESTRO lado (Hexagonal, CAPA 3): nada del proveedor sale de aquí ----

    public static class PaymentDeclinedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public PaymentDeclinedException(String reason) {
            super("Pago rechazado: " + reason);
        }
    }

    public static class PaymentProviderUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public PaymentProviderUnavailableException(String detail) {
            super("El proveedor de pagos no está disponible: " + detail);
        }
    }

    /*
        =================================================================
        CAPA 2) RESTCLIENT: EL BUILDER DE BOOT
        =================================================================

        Boot registra un bean RestClient.Builder YA CONFIGURADO: los mismos
        conversores de JSON que usa la aplicación, los tiempos límite de
        spring.http.clients.* (CAPA 4) y las métricas. Se inyecta el
        builder y se arma UN cliente por servicio externo, en el
        constructor:

            this.http = builder.baseUrl("https://api.pagos.example")
                               .defaultHeader("Accept", "application/json")
                               .build();

        No uses RestClient.create() en código de aplicación: crea uno "de
        fábrica" que ignora toda esa configuración.

        Las llamadas:

            Charge c = http.post().uri("/v1/charges")
                           .header("Idempotency-Key", key)
                           .body(request)                  // a JSON
                           .retrieve()                     // y si es 4xx/5xx: CAPA 3
                           .body(Charge.class);            // de JSON

            http.get().uri("/v1/charges/{id}", id)...      // {id}: se codifica solo
            .toEntity(Charge.class)                        // código + encabezados + cuerpo

        El cliente es seguro para usarse desde muchos hilos y reutiliza las
        conexiones TCP (v8_apis/part2_io_and_network/HttpClientUsage, CAPA
        7): se crea UNA vez, nunca uno por petición.
    */
    @Component
    public static class PaymentsGateway {
        private final RestClient http;

        public PaymentsGateway(RestClient.Builder builder, PaymentsProperties properties) {
            this.http = builder
                    .baseUrl(properties.baseUrl())
                    .defaultHeader("Accept", "application/json")
                    .build();
        }

        public Charge charge(String customerEmail, long amountCents, String idempotencyKey) {
            try {
                return http.post().uri("/v1/charges")
                        .header("Idempotency-Key", idempotencyKey)  // reintentar sin cobrar dos veces (CAPA 6)
                        .body(new ChargeRequest(customerEmail, amountCents))
                        .retrieve()
                        .body(Charge.class);
            } catch (HttpClientErrorException error) {             // 4xx: el proveedor dice que no (CAPA 3)
                ProblemDetail problem = error.getResponseBodyAs(ProblemDetail.class);
                throw new PaymentDeclinedException(problem != null ? problem.getTitle() : error.getStatusText());
            } catch (HttpServerErrorException | ResourceAccessException error) {   // 5xx o red caída
                throw new PaymentProviderUnavailableException(error.getMessage());
            }
        }

        public String slowReport() {
            return http.get().uri("/v1/slow-report").retrieve().body(String.class);   // CAPA 4
        }

        /*
            =============================================================
            CAPA 3) ERRORES: TRADUCIR LOS DEL PROVEEDOR A LOS TUYOS
            =============================================================

            Con retrieve(), una respuesta 4xx lanza HttpClientErrorException
            y una 5xx HttpServerErrorException (de Spring). Si esas suben
            hasta tu controller, terminan en un 500 genérico, y tu código
            de negocio queda atado a excepciones HTTP.

            Lo correcto (el adaptador de Hexagonal, CAPA 5): el cliente
            TRADUCE en la frontera:
            - 4xx del proveedor -> una excepción de TU dominio con la razón
              (PaymentDeclinedException). Si el proveedor responde con
              ProblemDetail (errors/ErrorHandling, CAPA 2), se lee su título;
            - 5xx o red caída -> "no disponible" (PaymentProviderUnavailableException),
              que tu advice puede convertir en 503 o en "inténtalo más tarde".

            Aquí, PaymentsGateway atrapa las excepciones de Spring y las
            convierte. error.getResponseBodyAs(ProblemDetail.class) lee el
            cuerpo del error con los mismos conversores JSON del cliente:
            nada de leer el texto a mano.

            Otras formas: .onStatus(...) después de retrieve() para un caso
            puntual; defaultStatusHandler(...) en el builder para TODAS las
            llamadas de un cliente (ShippingQuotes, CAPA 6); y para control
            total, .exchange((request, response) -> ...).
        */
    }

    /*
        =================================================================
        CAPA 4) TIEMPOS LÍMITE: SIEMPRE
        =================================================================

        Verificado en la metadata de Spring Boot 4.1.2: las propiedades

            spring.http.clients.connect-timeout    cuánto esperar para conectar
            spring.http.clients.read-timeout       cuánto esperar la respuesta

        NO TIENEN VALOR POR DEFECTO. Sin configurarlas, el cliente usa los
        del motor HTTP de abajo (por defecto, el HttpClient del JDK), que
        para leer la respuesta es esperar PARA SIEMPRE. Un proveedor que se
        cuelga deja colgados a tus hilos, y con ellos a tu servicio (la falla
        en cascada de MonolithVsMicroservices, CAPA 4).

        (En Boot 3.4/3.5 se llamaban spring.http.client.* en singular; en
        Boot 4 esas quedan obsoletas y avisan cuál usar.)

        Valores típicos: conexión 1 a 2 s; lectura según el servicio (un
        pago, 5 a 10 s; algo interno rápido, 500 ms). Y por servicio, con
        spring.http.serviceclient.<grupo>.read-timeout (CAPA 5).

        Medido en la prueba: con read-timeout de 1 s contra un servidor que
        tarda 3 s, la llamada falla al ~1 s con ResourceAccessException (en
        vez de esperar los 3 s, o para siempre).

        Una lección de las PRUEBAS: la primera versión usaba 300 ms, y falló
        de vez en cuando al correr todo el proyecto con la máquina cargada
        (la primera llamada de un cliente también inicializa conexiones y
        conversores). Una prueba con tiempos tan justos depende de la carga
        de la máquina: se vuelve inestable. Con margen (1 s contra 3 s) mide
        lo mismo y no falla por azar.
    */

    /*
        =================================================================
        CAPA 5) CLIENTES DECLARATIVOS: UNA INTERFACE Y SPRING LA IMPLEMENTA
        =================================================================

        En vez de escribir las llamadas a mano, se DECLARA el API como una
        interface con anotaciones parecidas a las de un controller, pero del
        lado del cliente:

            @HttpExchange("/v1/charges")
            interface PaymentsApi {
                @GetExchange("/{id}")  Charge find(@PathVariable String id);
                @PostExchange          Charge create(@RequestBody ChargeRequest r, ...);
            }

        Spring genera la implementación al arrancar: un PROXY (part1_core/
        .../container/IoCContainer, CAPA 8) que convierte cada llamada al
        método en la petición HTTP, sobre un RestClient.

        Desde Spring Framework 7 / Boot 4 se registran en grupo, sin
        escribir la fábrica a mano:

            @ImportHttpServices(group = "payments", types = PaymentsApi.class)
            spring.http.serviceclient.payments.base-url=https://api.pagos.example
            spring.http.serviceclient.payments.read-timeout=5s

        y PaymentsApi se inyecta como cualquier bean. (En Boot 3 se armaba con
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(cliente))
        .build().createClient(PaymentsApi.class), en un @Bean.)

        Cuándo usar cada uno: la interface para APIs con muchos endpoints y
        poca lógica por llamada; RestClient a mano cuando cada llamada
        necesita manejo especial. Los dos pueden traducir errores (con la
        interface, en el grupo o en un adaptador que la envuelve).
    */
    @HttpExchange("/v1/charges")
    public interface PaymentsApi {
        @GetExchange("/{id}")
        Charge find(@PathVariable String id);

        @PostExchange
        Charge create(@RequestBody ChargeRequest request, @RequestHeader("Idempotency-Key") String idempotencyKey);
    }

    /*
        =================================================================
        CAPA 6) REINTENTOS: SOLO LO QUE SE PUEDE REPETIR
        =================================================================

        Una red falla de vez en cuando: un 503 de un segundo, una conexión
        cortada. Reintentar lo resuelve... si la operación se puede REPETIR
        sin daño:
        - GET, PUT, DELETE: idempotentes (rest/RestControllers, CAPA 7);
        - POST: NO, salvo que lleve una clave de idempotencia (el
          Idempotency-Key de PaymentsGateway.charge: el proveedor devuelve el
          MISMO cargo si la clave se repite).

        Spring Framework 7 trae reintentos integrados (antes hacía falta otra
        librería): @Retryable en un método de un bean, y
        @EnableResilientMethods en la configuración. Es un proxy (CAPA 5):
        no funciona en llamadas dentro de la misma clase.

            @Retryable(includes = PaymentProviderUnavailableException.class,
                       maxRetries = 3, delay = 50, multiplier = 2)

        - includes: SOLO lo transitorio (5xx, red). Un 4xx ("tarjeta
          rechazada") no mejora reintentando;
        - espera creciente (50, 100, 200 ms...), idealmente con algo de azar
          (jitter: v13_concurrency/part1_threads/ConcurrencyProblems, CAPA 6);
        - pocos intentos: si el servicio está caído de verdad, reintentar
          empeora todo (para eso, el circuit breaker).

        La prueba: el proveedor responde 503 dos veces y luego 200; con
        @Retryable la llamada termina bien al tercer intento. (Spring 7
        también trae @ConcurrencyLimit, para limitar cuántas llamadas
        simultáneas hace un método: v13_concurrency/part2_coordination/
        Synchronization, CAPA 9, como anotación.)
    */
    @Component
    public static class ShippingQuotes {
        private final RestClient http;
        private final AtomicInteger attempts = new AtomicInteger();

        public ShippingQuotes(RestClient.Builder builder, PaymentsProperties properties) {
            this.http = builder.baseUrl(properties.baseUrl())
                    .defaultStatusHandler(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new PaymentProviderUnavailableException("HTTP " + response.getStatusCode().value());
                    })
                    .build();
        }

        @Retryable(includes = PaymentProviderUnavailableException.class, maxRetries = 3, delay = 50, multiplier = 2)
        public String quote(String postalCode) {            // GET: idempotente, se puede repetir
            attempts.incrementAndGet();
            return http.get().uri("/v1/quotes/{cp}", postalCode).retrieve().body(String.class);
        }

        public int attempts() {
            return attempts.get();
        }
    }

    /*
        =================================================================
        CAPA 7) PROBAR UN CLIENTE
        =================================================================

        Tres formas, de más rápida a más real:
        1) MockRestServiceServer (de spring-test): se "engancha" al
           RestClient.Builder y responde lo que le digas, sin red. Con
           @RestClientTest (Boot) se arma solo. Rápido; prueba el mapeo de
           JSON y de errores.
        2) Un servidor HTTP FALSO de verdad en un puerto local, como hace
           HttpClientsTest con el servidor del JDK: prueba también tiempos
           límite, encabezados y conexiones reales. En proyectos se usa
           mucho WireMock, una librería hecha justo para esto.
        3) Contra el sistema real (o su ambiente de pruebas): solo en pocas
           pruebas de integración, porque es lento y depende de otros.

        Y nunca una prueba que dependa de internet: si el proveedor tiene un
        mal día, se caen tus pruebas sin que tu código haya cambiado.
    */

    /*
        =================================================================
        CAPA 8) ERRORES COMUNES
        =================================================================

        1) RestTemplate o WebClient en código nuevo de Spring MVC; olvidar en
           Boot 4 el starter de restclient (CAPA 1).

        2) RestClient.create() o un cliente nuevo por cada petición (CAPA 2).

        3) Dejar que HttpClientErrorException llegue al negocio o al
           controller (CAPA 3).

        4) Ningún tiempo límite (el valor por defecto es esperar para
           siempre) (CAPA 4).

        5) Reintentar POST sin clave de idempotencia; reintentar 4xx;
           reintentos sin espera o infinitos (CAPA 6).

        6) @Retryable llamado desde la misma clase (es un proxy) (CAPA 6).

        7) Pruebas que llaman a servicios reales por internet (CAPA 7).

        8) Registrar en los logs el encabezado Authorization o el cuerpo
           con datos de tarjetas.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Hoy: RestClient (o interfaces @HttpExchange sobre él). En Boot 4,
          con spring-boot-starter-restclient.
        - Inyectar RestClient.Builder (ya configurado) y armar un cliente
          por servicio, una vez.
        - Traducir 4xx y 5xx del proveedor a excepciones propias en la
          frontera.
        - spring.http.clients.connect-timeout / read-timeout: sin valor por
          defecto; ponlos siempre.
        - @ImportHttpServices + spring.http.serviceclient.<grupo>.*: clientes
          declarativos registrados solos.
        - @Retryable (Spring 7) solo para fallas transitorias y operaciones
          idempotentes; con espera creciente y pocos intentos.
        - Probar con un servidor falso local; nunca contra internet.
    */
}
