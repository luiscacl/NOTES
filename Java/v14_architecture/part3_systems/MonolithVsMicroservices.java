package v14_architecture.part3_systems;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/*
    =====================================================================
    MONOLITO O MICROSERVICIOS: CUÁNDO PARTIR UN SISTEMA
    =====================================================================

    Ruta de architecture/: guía 6 de 6  (part3_systems)
    Anterior:  part3_systems/CommandsQueriesAndConsistency
    Siguiente: v15_spring (part1_core/.../startup/SpringAndBoot)  (última de architecture/)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Es una guía sobre todo de DECISIONES. Lo que se puede demostrar con
      código, se demuestra: el costo real de una llamada por red (con un
      servidor HTTP de verdad en 127.0.0.1), un circuit breaker y un
      monolito modular.

    Importancia: ALTA. Es una de las decisiones más caras de una empresa
    de software, y una pregunta frecuente en entrevistas.

    Índice:
        CAPA 1)  Las opciones: monolito, monolito modular, microservicios
        CAPA 2)  Lo que dan los microservicios
        CAPA 3)  Lo que cuestan: la red no es gratis
        CAPA 4)  Fallas parciales: timeouts, reintentos y circuit breaker
        CAPA 5)  El monolito modular: los límites sin la red
        CAPA 6)  Cada servicio es dueño de sus datos
        CAPA 7)  Dónde cortar, y cómo migrar sin reescribir todo
        CAPA 8)  Cómo decidir
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class MonolithVsMicroservices {

    public static void main(String[] args) throws Exception {
        check(networkCostExample(), "CAPA 3");
        check(circuitBreakerExample(), "CAPA 4");
        check(modularMonolithExample(), "CAPA 5");
        System.out.println("MonolithVsMicroservices: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) LAS OPCIONES: MONOLITO, MONOLITO MODULAR, MICROSERVICIOS
        =================================================================

        - MONOLITO: toda la aplicación es UN programa que se despliega
          junto (un .jar, un proceso). Pedidos, pagos, catálogo e inventario
          se llaman entre sí con llamadas normales de Java.

        - MONOLITO MODULAR: un solo programa, pero dividido por dentro en
          MÓDULOS con fronteras estrictas (cada uno con su API pública y sus
          datos), como si fueran servicios, pero sin la red (CAPA 5).

        - MICROSERVICIOS: cada parte es un programa APARTE, que se despliega
          y escala solo, con su propia base de datos, y se comunica con los
          demás por la RED (HTTP, mensajes).

        Y el peor de todos, que nadie elige a propósito:
        - MONOLITO DISTRIBUIDO: servicios separados que comparten base de
          datos, o que tienen que desplegarse juntos porque cada cambio
          toca varios. Tiene los costos de los microservicios sin ninguna de
          sus ventajas.

        "Monolito" NO es una mala palabra. Muchos sistemas grandes y
        exitosos son monolitos bien hechos.
    */

    /*
        =================================================================
        CAPA 2) LO QUE DAN LOS MICROSERVICIOS
        =================================================================

        - DESPLIEGUE INDEPENDIENTE: el equipo de pagos sube su cambio el
          martes sin coordinar con nadie. En un monolito grande, todos los
          cambios salen juntos y un error en uno detiene a todos.
        - ESCALADO INDEPENDIENTE: el catálogo recibe 100 veces más
          peticiones que los pagos; se ponen 20 copias del catálogo y 2 de
          pagos.
        - AISLAMIENTO DE FALLAS: si el servicio de recomendaciones se cae
          o se queda sin memoria, el checkout sigue (si se diseñó para eso:
          CAPA 4).
        - AUTONOMÍA DE EQUIPOS: cada equipo es dueño de un servicio de
          punta a punta.

        La razón de fondo casi siempre es ORGANIZACIONAL. La ley de Conway
        (1968): "un sistema termina copiando la estructura de comunicación
        de la organización que lo construye". Con 5 desarrolladores, un
        monolito es lo natural; con 200 en 20 equipos, coordinar un solo
        despliegue se vuelve el cuello de botella.
    */

    /*
        =================================================================
        CAPA 3) LO QUE CUESTAN: LA RED NO ES GRATIS
        =================================================================

        Partir un monolito convierte llamadas de método en llamadas por
        red. Las "falacias de la computación distribuida" (Peter Deutsch y
        otros en Sun, 1994) son lo que se suele suponer y es falso: la red
        es confiable, la latencia es cero, el ancho de banda es infinito, la
        red es segura, la topología no cambia...

        Medido: la misma operación (pedir un precio) como llamada de
        método y como llamada HTTP a otro servidor... en la MISMA máquina
        (127.0.0.1, sin salir a la red real):

            llamada de método              ~0.15 microsegundos
            llamada HTTP local             ~480 microsegundos

        Unas 3,000 veces más, y eso SIN red de verdad: entre máquinas de un
        centro de datos se suma la red física; entre regiones, decenas de
        milisegundos. Una pantalla que hacía 50 llamadas internas y ahora
        hace 50 llamadas remotas en serie suma segundos. Por eso las
        llamadas remotas se hacen en paralelo (v13_concurrency/part3_tasks/
        CompletableFutureUsage, CAPA 8) y se diseñan para pedir MÁS en cada
        una (el origen de los DTO: v11_patterns/part4_modern/DTO, CAPA 2).

        Además de la latencia:
        - CONSISTENCIA: ya no hay una transacción para todo; outbox, sagas e
          idempotencia (part3_systems/CommandsQueriesAndConsistency);
        - OPERACIÓN: N servicios que desplegar, monitorear, actualizar;
          seguir una petición a través de 6 servicios requiere "tracing"
          distribuido; logs repartidos;
        - PRUEBAS: probar un flujo completo necesita levantar varios
          servicios, o pruebas de contrato entre ellos;
        - VERSIONADO de las APIs entre servicios (DTO, CAPA 8).
    */
    static int localPrice(String sku) {
        return sku.length() * 1_000;
    }

    static boolean networkCostExample() throws IOException, InterruptedException {
        HttpServer pricingService = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        pricingService.createContext("/price", exchange -> {
            byte[] body = "4500".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        pricingService.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + pricingService.getAddress().getPort() + "/price")).build();
            long sum = 0;
            for (int i = 0; i < 200; i++) {                 // calentar las dos
                sum += Integer.parseInt(client.send(request, HttpResponse.BodyHandlers.ofString()).body());
                sum += localPrice("CAFE" + (i % 3));
            }
            long start = System.nanoTime();
            for (int i = 0; i < 500; i++) {
                sum += Integer.parseInt(client.send(request, HttpResponse.BodyHandlers.ofString()).body());
            }
            long remoteNanos = (System.nanoTime() - start) / 500;
            start = System.nanoTime();
            for (int i = 0; i < 500; i++) {
                sum += localPrice("CAFE" + (i % 3));
            }
            long localNanos = Math.max(1, (System.nanoTime() - start) / 500);
            return sum > 0 && remoteNanos > localNanos * 100;   // cientos o miles de veces más
        } finally {
            pricingService.stop(0);
        }
    }

    /*
        =================================================================
        CAPA 4) FALLAS PARCIALES: TIMEOUTS, REINTENTOS Y CIRCUIT BREAKER
        =================================================================

        En un monolito, si algo falla, falla todo junto. En un sistema
        distribuido, falla UNA parte mientras las demás siguen: la falla
        parcial. Y lo peor no es el servicio caído (responde error rápido),
        sino el LENTO: cada llamada se queda esperando, los hilos se
        acumulan, y el que llama también se cae. Una falla se propaga en
        cascada.

        Las defensas estándar:
        - TIMEOUT en toda llamada remota (v8_apis/part2_io_and_network/
          HttpClientUsage, CAPA 8): nunca esperar para siempre;
        - REINTENTOS con espera creciente y aleatoria (v13_concurrency/
          part1_threads/ConcurrencyProblems, CAPA 6), solo para lo que es
          seguro repetir (idempotente; CommandsQueriesAndConsistency,
          CAPA 7);
        - CIRCUIT BREAKER (interruptor, como el de la luz): si un servicio
          falló N veces seguidas, se deja de llamarlo por un tiempo y se
          responde de inmediato con un respaldo. Así no se gastan hilos
          esperando a alguien que está caído, y se le da aire para
          recuperarse. Pasado el tiempo, se deja pasar UNA llamada de
          prueba: si funciona, se cierra el circuito; si no, se vuelve a
          abrir.

            CERRADO --N fallas--> ABIERTO --pasa el tiempo--> MEDIO ABIERTO
               ^                                                  |
               +------------- la llamada de prueba funcionó ------+

        En proyectos reales viene de una librería (Spring tiene soporte
        para esto); aquí, uno pequeño para ver cómo funciona por dentro,
        con un reloj controlable para probarlo sin esperar
        (part2_boundaries/Hexagonal, CAPA 3).
    */
    enum BreakerState { CLOSED, OPEN, HALF_OPEN }

    static final class CircuitBreaker {
        private final int failureThreshold;
        private final Duration openFor;
        private final Supplier<Instant> clock;
        private BreakerState state = BreakerState.CLOSED;
        private int consecutiveFailures;
        private Instant openedAt;

        CircuitBreaker(int failureThreshold, Duration openFor, Supplier<Instant> clock) {
            this.failureThreshold = failureThreshold;
            this.openFor = openFor;
            this.clock = clock;
        }

        synchronized <T> T call(Supplier<T> remote, Supplier<T> fallback) {
            if (state == BreakerState.OPEN) {
                if (clock.get().isBefore(openedAt.plus(openFor))) {
                    return fallback.get();                  // abierto: ni lo intenta
                }
                state = BreakerState.HALF_OPEN;             // pasó el tiempo: una prueba
            }
            try {
                T result = remote.get();
                state = BreakerState.CLOSED;
                consecutiveFailures = 0;
                return result;
            } catch (RuntimeException e) {
                consecutiveFailures++;
                if (state == BreakerState.HALF_OPEN || consecutiveFailures >= failureThreshold) {
                    state = BreakerState.OPEN;
                    openedAt = clock.get();
                }
                return fallback.get();
            }
        }

        synchronized BreakerState state() {
            return state;
        }
    }

    static boolean circuitBreakerExample() {
        Instant[] now = {Instant.parse("2026-11-04T10:00:00Z")};    // reloj controlable
        CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30), () -> now[0]);
        int[] remoteCalls = {0};
        boolean[] serviceUp = {false};
        Supplier<String> recommendations = () -> {
            remoteCalls[0]++;
            if (!serviceUp[0]) {
                throw new IllegalStateException("recomendaciones caído");
            }
            return "taza, filtro";
        };
        Supplier<String> fallback = () -> "";               // la página, sin recomendaciones

        for (int i = 0; i < 3; i++) {
            breaker.call(recommendations, fallback);        // 3 fallas: se abre
        }
        boolean opened = breaker.state() == BreakerState.OPEN;
        for (int i = 0; i < 100; i++) {
            breaker.call(recommendations, fallback);        // 100 peticiones: ni una llega al servicio caído
        }
        boolean protectedService = remoteCalls[0] == 3;

        now[0] = now[0].plusSeconds(31);                    // pasa el tiempo; el servicio se recuperó
        serviceUp[0] = true;
        String afterRecovery = breaker.call(recommendations, fallback);   // la llamada de prueba
        return opened && protectedService
                && afterRecovery.equals("taza, filtro") && breaker.state() == BreakerState.CLOSED;
    }

    /*
        =================================================================
        CAPA 5) EL MONOLITO MODULAR: LOS LÍMITES SIN LA RED
        =================================================================

        La mayoría de los beneficios de diseño de los microservicios
        (fronteras claras, equipos dueños de una parte, datos propios) NO
        necesitan la red. Un MONOLITO MODULAR los tiene sin pagar la CAPA 3:

        - cada módulo (pedidos, facturación, inventario) es un paquete o un
          módulo de Maven (o de Java: v12_jvm/part2_classes/Modules), con
          UNA API pública pequeña (una fachada: v11_patterns/part2_structural/
          Facade, CAPA 6) y todo lo demás oculto (package-private);
        - un módulo no toca las tablas ni las clases internas de otro;
        - se comunican por esa API, o por EVENTOS dentro del mismo proceso
          (un bus como el de v11_patterns/part3_behavioral/Mediator, CAPA 4);
        - las fronteras se protegen con pruebas de arquitectura (part1_layers/
          LayeredArchitecture, CAPA 6).

        Y si algún día un módulo necesita escalar solo o desplegarse aparte,
        ya tiene sus fronteras: sacarlo a un servicio es cambiar las
        llamadas en proceso por llamadas por red (o los eventos en memoria
        por una cola), no rediseñar.

        El ejemplo: Pedidos publica OrderPlaced en un bus en memoria;
        Facturación lo escucha y genera la factura. Ninguno conoce las
        clases internas del otro: solo el evento, que es su contrato.
    */
    record OrderPlaced(long orderId, long totalCents) {     // el contrato entre módulos
    }

    static final class InProcessEventBus {
        private final List<Consumer<OrderPlaced>> subscribers = new ArrayList<>();

        void subscribe(Consumer<OrderPlaced> subscriber) {
            subscribers.add(subscriber);
        }

        void publish(OrderPlaced event) {
            subscribers.forEach(subscriber -> subscriber.accept(event));
        }
    }

    static final class OrdersModule {                       // su API pública: placeOrder
        private final InProcessEventBus bus;
        private long nextId = 1;

        OrdersModule(InProcessEventBus bus) {
            this.bus = bus;
        }

        long placeOrder(long totalCents) {
            long id = nextId++;
            bus.publish(new OrderPlaced(id, totalCents));   // avisa; no sabe quién escucha
            return id;
        }
    }

    static final class BillingModule {                      // no conoce a OrdersModule
        final List<String> invoices = new ArrayList<>();

        BillingModule(InProcessEventBus bus) {
            bus.subscribe(event -> invoices.add("factura del pedido " + event.orderId()
                    + " por " + event.totalCents() / 100 + " pesos"));
        }
    }

    static boolean modularMonolithExample() {
        InProcessEventBus bus = new InProcessEventBus();
        BillingModule billing = new BillingModule(bus);
        OrdersModule orders = new OrdersModule(bus);
        orders.placeOrder(25_000);
        orders.placeOrder(9_900);
        return billing.invoices.equals(List.of("factura del pedido 1 por 250 pesos",
                "factura del pedido 2 por 99 pesos"));
    }

    /*
        =================================================================
        CAPA 6) CADA SERVICIO ES DUEÑO DE SUS DATOS
        =================================================================

        La regla que separa los microservicios del monolito distribuido:
        CADA SERVICIO TIENE SU BASE DE DATOS (o al menos su esquema, que
        nadie más toca). Nadie lee ni escribe las tablas de otro: le pide
        los datos por su API, o mantiene su propia copia actualizada con
        eventos (part3_systems/CommandsQueriesAndConsistency, CAPA 2).

        Si dos servicios comparten tablas:
        - un cambio en una tabla obliga a cambiar y desplegar los dos
          juntos: el "despliegue independiente" se acabó;
        - las reglas del dueño (validaciones, invariantes del agregado) se
          saltan cuando otro escribe directo.

        Lo mismo aplica DENTRO de un monolito modular (CAPA 5): cada módulo
        con sus tablas. Es lo que hace posible separarlo después.
    */

    /*
        =================================================================
        CAPA 7) DÓNDE CORTAR, Y CÓMO MIGRAR SIN REESCRIBIR TODO
        =================================================================

        DÓNDE: por CONTEXTOS DELIMITADOS (part1_layers/DDDBasics, CAPA 9):
        catálogo, pedidos, facturación, envíos. Partes con su propio
        lenguaje y sus propios datos, que cambian por razones distintas.
        NUNCA por capa técnica (un "servicio de base de datos") ni por
        tabla (un "servicio de clientes" que solo es un CRUD que todos
        llaman para todo): eso produce servicios que se llaman entre sí en
        cada operación.

        Una prueba rápida: si casi todo cambio de negocio obliga a tocar dos
        servicios a la vez, la frontera está mal puesta.

        CÓMO: nunca "reescribamos todo en microservicios" de una vez. Se usa
        el patrón STRANGLER FIG (la higuera estranguladora: una planta que
        crece alrededor de un árbol hasta reemplazarlo; v10_design/
        part4_clean_code/Refactoring):
        1) se pone una entrada común (un proxy o API gateway) delante del
           monolito;
        2) se construye UN servicio nuevo para UNA parte, y la entrada manda
           a él solo esas peticiones;
        3) se repite; el monolito se va quedando con menos, hasta que (quizá)
           se apaga. En todo momento el sistema funciona.
    */

    /*
        =================================================================
        CAPA 8) CÓMO DECIDIR
        =================================================================

        La recomendación más repetida por quienes han hecho las dos cosas
        (Martin Fowler: "monolith first"): EMPEZAR con un monolito, BIEN
        MODULAR, y partir solo cuando una razón concreta lo pida:

            Señal                                          Respuesta
            ---------------------------------------------  --------------------------
            equipo pequeño (1 a 3 equipos)                  monolito modular
            el dominio todavía no se entiende bien          monolito (mover fronteras
                                                            dentro es barato; entre
                                                            servicios, carísimo)
            muchos equipos pisándose en cada despliegue     separar por contexto
            una parte necesita escalar muy distinto         sacar esa parte
            una parte necesita otra disponibilidad o        sacar esa parte
            seguridad (pagos)
            "porque así lo hace Netflix"                    no es una razón

        Y una condición previa: microservicios exigen madurez de
        OPERACIÓN (despliegue automatizado, monitoreo, logs centralizados,
        tracing). Sin eso, cada falla se vuelve una investigación entre
        varios sistemas.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Empezar con microservicios un producto nuevo con un equipo
           pequeño (CAPAS 2 y 8).

        2) Ignorar el costo de la red: llamadas remotas en serie, sin
           timeout, "conversaciones" de muchas llamadas pequeñas (CAPA 3).

        3) Llamadas remotas sin timeout, reintentos sin espera ni
           idempotencia, sin circuit breaker: fallas en cascada (CAPA 4).

        4) Un monolito "de bola de lodo" sin módulos, y querer resolverlo
           con microservicios: se obtiene una bola de lodo distribuida
           (CAPA 5).

        5) Base de datos compartida entre servicios: monolito distribuido
           (CAPA 6).

        6) Cortar por capa técnica o por tabla; reescribir todo de una vez
           (CAPA 7).

        7) Decidir por moda y no por una necesidad concreta (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Monolito, monolito modular, microservicios; evitar el monolito
          distribuido.
        - Microservicios dan despliegue y escalado independientes,
          aislamiento de fallas y autonomía de equipos (ley de Conway).
        - Cuestan: red (~3,000 veces más lenta que una llamada local,
          medido, sin salir de la máquina), consistencia, operación y
          pruebas.
        - Fallas parciales: timeouts, reintentos con espera aleatoria e
          idempotencia, circuit breaker.
        - El monolito modular da las fronteras sin la red, y deja la puerta
          abierta para separar después.
        - Cada servicio (y cada módulo) dueño de sus datos.
        - Cortar por contextos delimitados; migrar con strangler fig.
        - Empezar con un monolito modular; separar cuando una razón
          concreta lo pida.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
