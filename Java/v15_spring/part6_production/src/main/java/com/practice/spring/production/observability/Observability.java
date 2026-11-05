package com.practice.spring.production.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

/*
    =====================================================================
    OBSERVABILIDAD: SABER QUÉ PASA EN PRODUCCIÓN SIN ADIVINAR
    =====================================================================

    Ruta de spring/: guía 17  (part6_production)
    Anterior:  part5_security/.../jwt/JwtAndMethodSecurity
    Siguiente: part6_production/.../async/AsyncSchedulingCaching

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: ObservabilityTest, con el servidor
      real y Actuator. Desde v15_spring/:
          ./mvnw -pl part6_production test -Dtest=ObservabilityTest
    - Lo que Actuator expone "sin configurar nada" (CAPA 2) se midió con
      una aplicación aparte. El resto, en la prueba.

    Lo que ya se vio: medir antes de cambiar y los percentiles (v12_jvm/
    part3_performance/Performance), el log del GC y jcmd (v12_jvm/
    part1_memory/GarbageCollector), el errorId en los logs (part2_web/
    .../errors/ErrorHandling, CAPA 6), secretos (part1_core/.../config/
    ExternalConfiguration, CAPA 8).

    Importancia: MUY ALTA. Cuando algo falla a las 3 de la mañana, lo único
    que tienes es lo que la aplicación ya estaba registrando.

    Índice:
        CAPA 1)  Los tres pilares: logs, métricas y trazas
        CAPA 2)  Actuator: qué expone y qué no
        CAPA 3)  Salud: liveness y readiness
        CAPA 4)  Métricas: las de Spring y las tuyas
        CAPA 5)  Logs que una máquina puede leer
        CAPA 6)  Cambiar el nivel de log sin reiniciar
        CAPA 7)  Trazas: seguir una petición entre servicios
        CAPA 8)  Proteger Actuator
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class Observability {

    /*
        =================================================================
        CAPA 1) LOS TRES PILARES: LOGS, MÉTRICAS Y TRAZAS
        =================================================================

        "El checkout está lento" puede ser la base de datos, el proveedor de
        pagos, el GC, o un despliegue de hace una hora. Para responder sin
        adivinar, una aplicación en producción emite tres cosas:

            MÉTRICAS   NÚMEROS en el tiempo: peticiones por segundo, p99 de
                       latencia, errores, memoria, conexiones del pool. Baratas
                       y agregadas: dicen QUE algo pasa (y disparan alertas).
            LOGS       EVENTOS con detalle: "el pago del pedido 7 fue
                       rechazado". Dicen QUÉ pasó en un caso.
            TRAZAS     el CAMINO de una petición a través de varios servicios,
                       con cuánto tardó cada paso. Dicen DÓNDE se fue el
                       tiempo.

        La aplicación los EMITE; otros sistemas los guardan y los muestran:
        Prometheus (métricas) + Grafana (tableros y alertas), un sistema de
        logs (Elasticsearch, Loki, o el de tu nube), y uno de trazas (Tempo,
        Jaeger, Zipkin). En Spring, todo pasa por Actuator y Micrometer (la
        "SLF4J de las métricas": una API, muchos destinos).
    */
    @SpringBootApplication
    public static class ObservedShopApplication {
    }

    /*
        =================================================================
        CAPA 2) ACTUATOR: QUÉ EXPONE Y QUÉ NO
        =================================================================

        spring-boot-starter-actuator agrega endpoints de administración bajo
        /actuator. Medido, SIN configurar nada, solo uno está expuesto por
        HTTP: /actuator/health. Con management.endpoints.web.exposure.include=*
        aparecen 19, entre ellos:

            health       ¿está viva? ¿está lista? (CAPA 3)
            metrics      las métricas, una por una (CAPA 4)
            prometheus   todas, en el formato que lee Prometheus
            loggers      ver y CAMBIAR niveles de log (CAPA 6)
            info         versión, commit, lo que quieras publicar
            env, configprops   la configuración EFECTIVA (con los valores
                         ocultos: medido, hasta java.home sale "******")
            beans, conditions, mappings   el contenedor por dentro
                         (part1_core/.../autoconfig/AutoConfigurationUsage, CAPA 6)
            threaddump   el volcado de hilos (como jcmd Thread.print)
            sbom         la lista de librerías que trae la aplicación (para
                         revisar vulnerabilidades)

        Cerrado por defecto porque varios exponen información sensible
        (CAPA 8). Se abre SOLO lo necesario:
            management.endpoints.web.exposure.include=health,info,metrics,prometheus
    */

    /*
        =================================================================
        CAPA 3) SALUD: LIVENESS Y READINESS
        =================================================================

        /actuator/health responde {"status":"UP"} (200) o DOWN (503). Spring
        Boot suma INDICADORES: la base de datos, el disco, y los tuyos.

        Los orquestadores (Kubernetes, y los balanceadores de carga) hacen
        DOS preguntas distintas, y Actuator trae un grupo para cada una
        (medido: "groups":["liveness","readiness"] por defecto):

            /actuator/health/liveness   ¿el proceso está VIVO? Si no
                                        (un deadlock, memoria agotada), se
                                        REINICIA el contenedor.
            /actuator/health/readiness  ¿puede ATENDER ahora? Si no (arrancando,
                                        una dependencia caída), se le QUITA el
                                        tráfico, sin reiniciarlo.

        El error clásico: meter la base de datos en LIVENESS. Si la base de
        datos se cae, Kubernetes reinicia TODAS las copias de la aplicación...
        que siguen sin base de datos, en un ciclo de reinicios que no arregla
        nada. Las dependencias externas van en READINESS (dejar de recibir
        tráfico hasta que vuelvan); liveness solo revisa el propio proceso.

        PaymentGatewayHealth es un indicador propio, incluido en readiness
        (management.endpoint.health.group.readiness.include). La prueba lo
        "tumba": readiness responde 503 DOWN, y liveness sigue 200 UP.
    */
    @Component("paymentGateway")
    public static class PaymentGatewayHealth implements HealthIndicator {
        private final AtomicBoolean reachable = new AtomicBoolean(true);

        @Override
        public Health health() {
            return reachable.get()
                    ? Health.up().withDetail("provider", "pagos.example").build()
                    : Health.down().withDetail("provider", "pagos.example").withDetail("error", "timeout al conectar").build();
        }

        public void simulateOutage(boolean down) {          // en la vida real: un ping al proveedor, con caché
            reachable.set(!down);
        }
    }

    /*
        =================================================================
        CAPA 4) MÉTRICAS: LAS DE SPRING Y LAS TUYAS
        =================================================================

        Sin escribir nada, Micrometer mide: peticiones HTTP (cuántas, cuánto
        tardaron, por ruta, método y código), la JVM (memoria por zona,
        recolecciones, hilos), el pool de conexiones, Tomcat, el sistema.

        Medido, después de tres POST a /api/orders?tier=GOLD, ?tier=GOLD y
        ?tier=REGULAR, la métrica http.server.requests tenía UNA serie con
        uri="/api/orders" y count 3. Usa la PLANTILLA de la ruta, no la URL
        real: sin parámetros y, en rutas como /api/products/{id}, sin el id.
        Si guardara cada valor, habría una serie por producto. Eso se llama explosión de
        CARDINALIDAD: millones de series que tumban Prometheus. Nunca pongas
        en una etiqueta (tag) un valor sin límite: ids, correos, URLs
        completas.

        Las métricas del NEGOCIO las agregas tú, con el MeterRegistry:

            Counter   algo que solo sube: pedidos creados (por nivel de
                      cliente: una etiqueta con POCOS valores posibles)
            Timer     cuánto tarda algo: la llamada al proveedor de pagos
                      (con percentiles: v12_jvm/part3_performance/Performance)
            Gauge     un valor que sube y baja: pedidos en cola

        En Prometheus se ven así (medido):
            shop_orders_placed_total{tier="GOLD"} 2.0
            shop_orders_placed_total{tier="REGULAR"} 1.0
            shop_payment_duration_seconds{quantile="0.99"} 0.006291456
            http_server_requests_seconds_count{...,status="201",uri="/api/orders"} 3

        Con eso se arman tableros ("pedidos por minuto") y ALERTAS ("los
        errores 5xx pasan del 1% por 5 minutos").

        Dos cosas que aparecieron al PROBAR esto:
        - en las pruebas (@SpringBootTest), Spring Boot APAGA la exportación de
          métricas a sistemas externos, para no mandar datos de prueba a un
          monitoreo real. Medido: /actuator/prometheus respondió 404 en la
          prueba, aunque en la aplicación normal existía. Se reactiva con
          @AutoConfigureMetrics (Boot 4; en Boot 3, @AutoConfigureObservability);
        - las métricas se ACUMULAN mientras vive el contexto, y el contexto se
          comparte entre pruebas: una prueba que esperaba "2 pedidos GOLD"
          vio 3, porque otra ya había creado uno. Se compara la DIFERENCIA
          contra el valor de antes, no el total.
    */
    @RestController
    public static class OrderController {
        private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);
        private final MeterRegistry metrics;
        private final Timer paymentTimer;

        public OrderController(MeterRegistry metrics) {
            this.metrics = metrics;
            this.paymentTimer = Timer.builder("shop.payment.duration")
                    .description("Cuánto tarda el proveedor de pagos")
                    .publishPercentiles(0.5, 0.99)
                    .register(metrics);
        }

        @PostMapping("/api/orders")
        @ResponseStatus(HttpStatus.CREATED)
        public Map<String, String> place(@RequestParam String tier) {
            paymentTimer.record(() -> sleepMillis(5));       // "la llamada al banco", medida
            Counter.builder("shop.orders.placed")
                    .tag("tier", tier)                       // pocos valores: REGULAR, GOLD
                    .register(metrics)
                    .increment();
            LOG.info("Pedido creado para un cliente {}", tier);   // CAPA 5: con el requestId del MDC
            return Map.of("status", "PAID");
        }

        private static void sleepMillis(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /*
        =================================================================
        CAPA 5) LOGS QUE UNA MÁQUINA PUEDE LEER
        =================================================================

        Un log en texto ("2026-11-04 10:00 INFO Pedido creado...") lo lee bien
        una persona, y mal un sistema que busca entre millones. Desde Spring
        Boot 3.4, una propiedad los vuelve JSON ESTRUCTURADO:

            logging.structured.format.console=ecs     (o logstash, gelf)

        Medido, cada línea queda así (formato ECS de Elastic):

            {"@timestamp":"2026-11-05T00:30:26.92Z","log":{"level":"INFO",
             "logger":"..."},"process":{"pid":50916,"thread":{"name":"main"}},
             "message":"...","ecs":{"version":"8.11"}}

        y se puede buscar por campo: todos los ERROR de un logger, de un hilo.

        El dato más útil en un log: un ID DE PETICIÓN que aparece en TODAS
        las líneas de esa petición. Se pone en el MDC (Mapped Diagnostic
        Context: un mapa por hilo, un ThreadLocal, que el log agrega a cada
        línea). RequestIdFilter toma el encabezado X-Request-Id (o inventa
        uno), lo pone en el MDC al empezar y lo QUITA al terminar (los hilos
        de Tomcat se reusan: v13_concurrency/part1_threads/ThreadBasics,
        CAPA 8). La prueba captura la salida y encuentra "requestId":"req-42"
        en la línea del pedido.

        Una sorpresa medida al agregar la guía siguiente: Logback se configura
        UNA vez por proceso. En las pruebas, varias aplicaciones comparten la
        JVM, y la PRIMERA que arranca decide el formato: cuando otra clase de
        prueba arrancó antes con logs de texto, la propiedad ecs de
        ObservabilityTest se ignoró y la prueba falló. Por eso el formato va
        en src/test/resources/application.properties, igual para todas.

        Qué NO va en un log: contraseñas, tokens, números de tarjeta, datos
        personales de más. Los logs se guardan meses y los lee mucha gente
        (v11_patterns/part4_modern/DTO, CAPA 5).
    */
    @Component
    public static class RequestIdFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String requestId = request.getHeader("X-Request-Id");
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }
            MDC.put("requestId", requestId);
            response.setHeader("X-Request-Id", requestId);   // el cliente lo puede reportar
            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove("requestId");                     // siempre: el hilo vuelve al pool
            }
        }
    }

    /*
        =================================================================
        CAPA 6) CAMBIAR EL NIVEL DE LOG SIN REINICIAR
        =================================================================

        En producción el nivel suele ser INFO: DEBUG escribiría demasiado. Pero
        cuando hay un problema, quieres DEBUG de UN paquete, AHORA, sin
        reiniciar (reiniciar puede hacer desaparecer el problema).

            POST /actuator/loggers/com.practice.spring.production
            {"configuredLevel": "DEBUG"}

        cambia el nivel en caliente (la prueba lo hace y lo comprueba con un
        GET). Se regresa a INFO cuando se termina de investigar. Por eso
        loggers es de los endpoints que se exponen... protegido (CAPA 8).
    */

    /*
        =================================================================
        CAPA 7) TRAZAS: SEGUIR UNA PETICIÓN ENTRE SERVICIOS
        =================================================================

        Con microservicios, una petición del usuario pasa por el gateway, el
        servicio de pedidos, el de inventario y el de pagos
        (v14_architecture/part3_systems/MonolithVsMicroservices). Una TRAZA
        le da a esa petición un trace id único, y cada paso es un SPAN (con
        su inicio, su duración y su servicio). El trace id viaja en un
        encabezado estándar (traceparent, de W3C Trace Context) en cada
        llamada HTTP o mensaje, así cada servicio sabe a qué traza pertenece.

        Spring lo hace con Micrometer Tracing + OpenTelemetry (el estándar
        abierto actual): con sus dependencias y la URL del sistema de
        trazas, cada petición entrante, cada llamada con RestClient y cada
        consulta crea su span solo, y el trace id aparece en cada línea de
        log (en el MDC, como el requestId de la CAPA 5). Así, desde una línea
        de error se salta a la traza completa y se ve qué servicio tardó.

        No se incluye en las pruebas de esta guía porque necesita un sistema
        de trazas corriendo; la idea es la misma que el requestId, extendida
        a todos los servicios.
    */

    /*
        =================================================================
        CAPA 8) PROTEGER ACTUATOR
        =================================================================

        env, configprops, beans, heapdump y threaddump revelan la
        configuración, la estructura interna, y hasta datos en memoria (un
        heap dump tiene TODO lo que había en memoria: v12_jvm/part1_memory/
        GarbageCollector, CAPA 6). Expuestos a internet son un regalo para
        un atacante. Las prácticas:

        - exponer SOLO lo necesario (CAPA 2);
        - un PUERTO aparte para administración (management.server.port=8081)
          que no se publica hacia internet: solo lo alcanzan Prometheus y
          Kubernetes desde la red interna;
        - y protegerlo con Spring Security (part5_security): health
          público, el resto con un rol de operación;
        - los valores de env y configprops se muestran ocultos por defecto
          (medido: "******"); no lo cambies en producción
          (management.endpoint.env.show-values).
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Investigar a ciegas: sin métricas ni logs útiles cuando pasa el
           problema (CAPA 1).

        2) exposure.include=* en producción (CAPAS 2 y 8).

        3) Dependencias externas en liveness: reinicios en cadena (CAPA 3).

        4) Etiquetas de métricas con ids o correos: explosión de
           cardinalidad (CAPA 4).

        5) Logs en texto libre sin id de petición; datos sensibles en los
           logs; MDC sin limpiar (CAPA 5).

        6) DEBUG permanente en producción, o reiniciar para cambiar el nivel
           (CAPA 6).

        7) Microservicios sin trazas distribuidas (CAPA 7).

        8) Actuator en el mismo puerto público y sin protección (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Métricas (que algo pasa), logs (qué pasó), trazas (dónde).
        - Actuator: solo health expuesto por defecto; abrir lo necesario.
        - liveness = el proceso; readiness = puede atender (las
          dependencias van aquí).
        - Micrometer: métricas automáticas (HTTP por plantilla de ruta, JVM,
          pool) y las del negocio (Counter, Timer, Gauge); cuidado con la
          cardinalidad; Prometheus.
        - Logs JSON con logging.structured.format.console; requestId en el
          MDC.
        - /actuator/loggers cambia niveles en caliente.
        - Trazas con Micrometer Tracing + OpenTelemetry y traceparent.
        - Actuator en un puerto interno y protegido.
    */
}
